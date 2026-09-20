package com.boxue.module.order.mq;

import com.boxue.config.RabbitConfig;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 业务死信消费者：监听 order.dead.queue。
 *
 * 走到这里的消息 = 重试 3 次仍处理失败的业务消息，必须人工介入。
 * 真实项目通常：
 * 1. 告警：发钉钉/邮件通知值班人员
 * 2. 兜底：把消息落一张"死信表"，定时任务扫描重放，或人工在管理界面排查
 * 学习项目里先用日志把"谁死了、为什么死"完整记下来，方便观察整条链路。
 *
 * 注意：acknowledge-mode: manual 是全局配置，这里也必须手动 ack（记录日志后再确认），
 * 否则消息会一直卡在 unacked 不回队列也不删除（踩坑记录 2026-09-20）。
 */
@Slf4j
@Component
public class DeadLetterConsumer {

    @RabbitListener(queues = RabbitConfig.DEAD_QUEUE)
    public void onDeadMessage(Message message, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String body = new String(message.getBody());
        log.error("[真死信] 业务死信队列收到消息，需人工处理：body={}", body);
        // 生产环境这里发告警 + 落死信表 + 定时任务重放，做完再 ack
        channel.basicAck(deliveryTag, false);
        log.info("[真死信] 死信已记录并 ack，deliveryTag={}", deliveryTag);
    }
}