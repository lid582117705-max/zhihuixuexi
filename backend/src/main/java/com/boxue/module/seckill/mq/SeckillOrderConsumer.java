package com.boxue.module.seckill.mq;

import com.boxue.config.RabbitConfig;
import com.boxue.module.order.entity.Order;
import com.boxue.module.order.service.OrderService;
import com.boxue.module.seckill.dto.SeckillMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * 秒杀落单消费者：从 MQ 取"抢购成功"的消息，慢慢落库建订单（削峰填谷）
 *
 * 可靠性三板斧（复用加餐C 的经验）：
 * 1. 幂等：Redis SETNX 按消息 uniqueId 记"已处理"，重复消息直接跳过，避免重复建单
 * 2. 手动确认：订单建成功才 ack
 * 3. 失败：显式 nack(requeue=false) → 进业务死信人工处理（幂等标记会留存，人工处理后需清理）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private static final String CONSUME_KEY_PREFIX = "seckill:consume:";

    private final OrderService orderService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.SECKILL_QUEUE)
    public void onSeckillMessage(Message message, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            SeckillMessage msg = objectMapper.readValue(new String(message.getBody()), SeckillMessage.class);

            // 幂等：同一条消息处理过了就不再建单（MQ 可能重复投递）
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(CONSUME_KEY_PREFIX + msg.getUniqueId(), "1", Duration.ofHours(1));
            if (Boolean.FALSE.equals(first)) {
                log.info("[秒杀落单] 消息 {} 已处理过，跳过", msg.getUniqueId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 核心：创建秒杀订单（普通待支付订单，15 秒不支付同样走延时关单链路）
            Order order = orderService.createSeckillOrder(msg.getUserId(), msg.getCourseId());
            log.info("[秒杀落单] 用户 {} 抢购课程 {} 订单已落库：{}", msg.getUserId(), msg.getCourseId(), order.getOrderNo());

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 失败：拒绝且不回队列 → 进死信人工处理
            log.error("[秒杀落单] 处理失败，消息进死信队列", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}