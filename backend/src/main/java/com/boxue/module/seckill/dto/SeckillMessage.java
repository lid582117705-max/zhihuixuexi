package com.boxue.module.seckill.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒杀消息：抢购成功后发给 MQ 的消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage {

    /** 消息唯一 id：消费者用它做幂等标记，防止重复消费建重复订单 */
    private String uniqueId;
    /** 抢到的人 */
    private Long userId;
    /** 抢到的课程 */
    private Long courseId;
}