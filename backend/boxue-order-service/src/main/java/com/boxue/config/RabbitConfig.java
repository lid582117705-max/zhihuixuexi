package com.boxue.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置：
 * 1. 延时关单链路：TTL + 死信队列(DLX)（阶段3）
 * 2. 业务死信链路：close.queue 消费失败重试耗尽 → 真死信队列（加餐C）
 * 3. 生产者确认 + 路由失败回调（加餐C）
 *
 * 两条死信链路的区别：
 * - order.dlx.exchange      ：延时用（delay.queue 的 TTL 到期 → 转给消费者）
 * - order.business.dlx...   ：业务用（close.queue 处理失败 → 进死信等人工）
 */
@Slf4j
@Configuration
@EnableRabbit
@RequiredArgsConstructor
public class    RabbitConfig {

    /** 延迟队列：下单后消息先进这里"睡觉" */
    public static final String DELAY_QUEUE = "order.delay.queue";
    /** 延时死信交换机：接收 TTL 到期"死掉"的消息 */
    public static final String DLX_EXCHANGE = "order.dlx.exchange";
    /** 关单队列：真正干活的消费者监听这里 */
    public static final String CLOSE_QUEUE = "order.close.queue";
    /** 延时死信带的路由键，用来把死信送进关单队列 */
    public static final String CLOSE_ROUTING_KEY = "order.close";

    /** 业务死信交换机：close.queue 里处理失败的消息进这里 */
    public static final String BUSINESS_DLX_EXCHANGE = "order.business.dlx.exchange";
    /** 业务死信队列：重试耗尽仍失败的消息，等人工处理 */
    public static final String DEAD_QUEUE = "order.dead.queue";
    /** 秒杀队列：抢购成功先发消息进来，消费者慢慢落库（削峰填谷） */
    public static final String SECKILL_QUEUE = "seckill.order.queue";

    /** 业务死信路由键 */
    public static final String DEAD_ROUTING_KEY = "order.dead";

    /** 延迟队列：消息最长活 15 秒（演示用），到期变成死信转给延时死信交换机 */
    @Bean
    public Queue delayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 15000);                         // 消息最长存活 15 秒
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);         // 死信转给这个交换机
        args.put("x-dead-letter-routing-key", CLOSE_ROUTING_KEY); // 死信附带路由键
        return new Queue(DELAY_QUEUE, true, false, false, args);
    }

    /** 延时死信交换机（Direct 型：按路由键精确匹配） */
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    /** 关单队列：真正被消费者监听的队列 */
    @Bean
    public Queue closeQueue() {
        Map<String, Object> args = new HashMap<>();
        // 加餐C：消费失败重试耗尽后，消息被拒收 → 进业务死信队列
        args.put("x-dead-letter-exchange", BUSINESS_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", DEAD_ROUTING_KEY);
        return new Queue(CLOSE_QUEUE, true, false, false, args);
    }

    /** 延时绑定：把关单队列绑到延时死信交换机上，路由键 order.close */
    @Bean
    public Binding closeBinding() {
        return BindingBuilder.bind(closeQueue()).to(dlxExchange()).with(CLOSE_ROUTING_KEY);
    }

    /** 秒杀队列：抢购成功 → 发消息 → 消费者慢慢落库；处理失败重试耗尽 → 复用业务死信 */
    @Bean
    public Queue seckillQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", BUSINESS_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", DEAD_ROUTING_KEY);
        return new Queue(SECKILL_QUEUE, true, false, false, args);
    }

    /** 业务死信交换机：Close.queue 失败消息的归宿 */
    @Bean
    public DirectExchange businessDlxExchange() {
        return new DirectExchange(BUSINESS_DLX_EXCHANGE, true, false);
    }

    /** 真死信队列：人工处理入口（本项目用一个消费者打日志兜底） */
    @Bean
    public Queue deadQueue() {
        return new Queue(DEAD_QUEUE, true);
    }

    /** 业务死信绑定 */
    @Bean
    public Binding deadBinding() {
        return BindingBuilder.bind(deadQueue()).to(businessDlxExchange()).with(DEAD_ROUTING_KEY);
    }

    /**
     * 自定义 RabbitTemplate：全局开启"生产者确认 + 路由失败回调"。
     * 用配置类里定义的这个 Bean 覆盖 Spring Boot 自动配置的 RabbitTemplate，
     * 业务代码里注入的就是它，"下单发消息"自动带上确认能力。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // mandatory=true：消息路由不到任何队列时，触发下面的 returns 回调（而不是被 broker 静默丢弃）
        template.setMandatory(true);

        // 生产者确认回调：broker 收到消息并落盘后回调。ack=true 收到；ack=false 被拒收（要补偿重发）
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String id = correlationData == null ? "null" : correlationData.getId();
            if (ack) {
                log.info("[MQ确认] 消息已到达交换机，correlationId={}", id);
            } else {
                log.error("[MQ确认] 交换机拒收消息，correlationId={}，原因={}", id, cause);
                // 真实项目：这里做补偿（重发 or 写一张"待发消息表"定时扫描重发）
            }
        });

        // 路由失败回调：消息在交换机上找不到能匹配的队列
        template.setReturnsCallback(returned -> {
            log.error("[MQ路由失败] 消息路由不到任何队列：exchange={}, routingKey={}, body={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    new String(returned.getMessage().getBody()));
        });
        return template;
    }
}