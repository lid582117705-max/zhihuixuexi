package com.boxue.module.order.mq;

import com.boxue.config.RabbitConfig;
import com.boxue.module.order.entity.DeadLetter;
import com.boxue.module.order.service.DeadLetterService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 真死信消费者：order.dead.queue 的"接管人"
 *
 * 在它之前，重试耗尽的消息进了死信队列就没人管了——不落库、不告警、不 ack，
 * 消息永久堆在 broker 里，只能靠人打开 15672 用眼睛看。
 *
 * 它做四件事：
 * 1. 解析 x-death 头，拿到"消息死前在哪个队列"，重放时才知道投回哪
 * 2. 落库留档（现场：消息体、来源、时间）
 * 3. 打 error 日志 + 告警（生产在这里接钉钉/企微机器人）
 * 4. basicAck 出队，避免死信队列无限堆积
 *
 * 注意：全局 acknowledge-mode=manual，所以这里也必须手动 ack。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final DeadLetterService deadLetterService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.DEAD_QUEUE)
    public void onDeadMessage(Message message, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);

            DeadLetter record = new DeadLetter();
            record.setMessageId(resolveMessageId(message));
            record.setSourceQueue(resolveSourceQueue(message));
            record.setSourceExchange(message.getMessageProperties().getReceivedExchange());
            record.setSourceRoutingKey(message.getMessageProperties().getReceivedRoutingKey());
            record.setBizKey(resolveBizKey(body));
            record.setBody(body);
            record.setStatus(0);
            record.setReplayCount(0);
            record.setCreateTime(LocalDateTime.now());

            deadLetterService.save(record);

            log.error("[真死信] 已落库 id={}，原队列={}，body={}",
                    record.getId(), record.getSourceQueue(), body);
            // 生产环境在这里发告警，例如：
            // alertService.send("MQ 死信告警：原队列=" + record.getSourceQueue() + "，body=" + body);

            // 落库成功才 ack：确认"我已接管"，消息从 MQ 移除，不再堆积
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 连落库都失败（比如 DB 挂了）：既不能 ack（会丢现场），
            // 也不能 nack（requeue 会死循环，requeue=false 会再进一次死信自己）
            // 只能保持 unacked，让消息留在 broker 等人工处理
            log.error("[真死信] 处理死信本身失败，消息保持未确认状态待人工处理", e);
        }
    }

    /** 消息唯一标识：优先用原消息 messageId，没有就生成一个 */
    private String resolveMessageId(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        return messageId != null ? messageId : UUID.randomUUID().toString();
    }

    /**
     * 从 x-death 头解析"消息死前所在的队列"
     *
     * RabbitMQ 每次消息变成死信，都会往 x-death 数组头部插一条记录，结构大致是：
     * [{ count:1, reason:"rejected", queue:"order.close.queue",
     *    exchange:"order.business.dlx.exchange", "routing-keys":["order.dead"], time:... }]
     *
     * 取第 0 个（最新的）的 queue，就是消息被拒时所在的队列。
     */
    private String resolveSourceQueue(Message message) {
        Object header = message.getMessageProperties().getHeader("x-death");
        if (header instanceof List<?> deaths && !deaths.isEmpty()
                && deaths.get(0) instanceof Map<?, ?> first) {
            Object queue = first.get("queue");
            if (queue != null) {
                return queue.toString();
            }
        }
        return null;
    }

    /**
     * 尝试从消息体解析业务幂等键（秒杀消息的 uniqueId）。
     * 关单消息的 body 是纯订单 id（"123"），解析 JSON 会失败，返回 null 即可。
     */
    private String resolveBizKey(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode uniqueId = node.get("uniqueId");
            return uniqueId == null ? null : uniqueId.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
