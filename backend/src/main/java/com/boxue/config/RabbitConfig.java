package com.boxue.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置：用 TTL + 死信队列(DLX) 实现"延迟消息"
 *
 * 消息旅程：
 * 下单 --▶ order.delay.queue（延迟队列：没人消费，TTL 15 秒）
 *             │ 到期后消息过期 → 变成死信
 *             ▼
 *          order.dlx.exchange（死信交换机）
 *             ▼
 *          order.close.queue（真正被消费者消费的队列）
 */
@Configuration
@EnableRabbit
public class RabbitConfig {

    /** 延迟队列：下单后消息先进这里"睡觉" */
    public static final String DELAY_QUEUE = "order.delay.queue";
    /** 死信交换机：接收过期死掉的消息 */
    public static final String DLX_EXCHANGE = "order.dlx.exchange";
    /** 关单队列：真正干活的消费者监听这里 */
    public static final String CLOSE_QUEUE = "order.close.queue";
    /** 死信带的路由键，用来把死信送进关单队列 */
    public static final String CLOSE_ROUTING_KEY = "order.close";

    /** 延迟队列：消息最长活 15 秒（演示用，真实项目一般 30 分钟），到期变成死信转给死信交换机 */
    @Bean
    public Queue delayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 15000);                       // 消息最长存活 15 秒
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);       // 死信转给这个交换机
        args.put("x-dead-letter-routing-key", CLOSE_ROUTING_KEY); // 死信附带路由键
        return new Queue(DELAY_QUEUE, true, false, false, args);
    }

    /** 死信交换机（Direct 型：按路由键精确匹配） */
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    /** 关单队列：真正被消费者监听的队列 */
    @Bean
    public Queue closeQueue() {
        return new Queue(CLOSE_QUEUE, true);
    }

    /** 绑定规则：把关单队列绑到死信交换机上，路由键 order.close */
    @Bean
    public Binding closeBinding() {
        return BindingBuilder.bind(closeQueue()).to(dlxExchange()).with(CLOSE_ROUTING_KEY);
    }
}