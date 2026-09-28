package com.boxue.module.order.mq;

import com.boxue.config.RabbitConfig;
import com.boxue.module.order.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单关单消费者：收到延迟消息（内容为订单 id）后，检查订单是否"待支付"，是则关闭为"已取消"。
 *
 * 加餐C 手动确认 + 手动重试（代码自己实现，不用 Spring listener.retry）：
 * 1. 成功 → channel.basicAck 明确告诉 broker 这条处理完了，可以删
 * 2. 失败 → 本地重试 3 次（1 秒、2 秒退避），全程消息处于 unacked，别的消费者碰不到
 * 3. 重试完仍失败 → channel.basicNack(tag, false, false)：明确"拒绝且不回队列"，
 *    broker 按 close.queue 上的死信参数把消息转投 order.dead.queue（人工处理）
 *
 * 踩坑记录（2026-09-20）：
 * acknowledge-mode=manual 下，Spring retry 耗尽后默认 RejectAndDontRequeueRecoverer 的
 * 隐式拒收不可靠——消息会一直卡在 unacked 状态，既不回队列也不进死信。
 * 所以本项目自己实现重试 + 显式 nack，每一层都在自己代码里，看得见、控得住。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseConsumer {

    /** 最多尝试几次（第 1 次 + 2 次重试） */
    private static final int MAX_ATTEMPTS = 3;

    private final OrderService orderService;

    @RabbitListener(queues = RabbitConfig.CLOSE_QUEUE)
    public void onCloseMessage(Message message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String orderIdStr = new String(message.getBody());

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                // 干正事：关单（内部是原子更新 WHERE status=0，天然幂等，重试/重复消息无害）
                orderService.closeIfUnpaid(Long.valueOf(orderIdStr));

                // 成功 → 手动确认，broker 删除这条消息
                channel.basicAck(deliveryTag, false);
                log.info("[消费者] 第 {} 次尝试成功，订单 {} 已处理并 ack", attempt, orderIdStr);
                return; // 处理成功，结束
            } catch (Exception e) {
                log.warn("[消费者] 第 {} 次尝试失败（{}），等待后重试", attempt, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(1000L * attempt); // 退避：第1次重试等1秒，第2次等2秒
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // 恢复中断标志，让上层知道
                        throw new IOException(ie);
                    }
                }
            }
        }

        // 重试完仍失败 → 明确拒绝且不回队列 → broker 转投死信队列
        channel.basicNack(deliveryTag, false, false);
        log.error("[消费者] 重试 {} 次仍失败，消息已拒绝并进入死信队列，body={}", MAX_ATTEMPTS, orderIdStr);
    }
}