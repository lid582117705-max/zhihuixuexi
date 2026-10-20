package com.boxue.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 死信记录：把 order.dead.queue 里的消息落库留档
 *
 * 为什么不直接看 MQ 管理界面？
 * 1. 管理界面只能一条条点开看，不能按状态/时间筛选
 * 2. 消息被删就永远查不到，没有历史
 * 3. 无法统计"今天失败了多少条、什么原因最多"
 * 4. 重投要手工填 exchange/routingKey，量大做不了
 */
@Data
@TableName("dead_letter")
public class DeadLetter {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息唯一标识（原消息 messageId，没有则生成 UUID） */
    private String messageId;

    /** 消息死前所在的队列（从 x-death 头解析），重放时投回这里 */
    private String sourceQueue;

    /** 死信来源交换机（order.business.dlx.exchange） */
    private String sourceExchange;

    /** 死信路由键（order.dead） */
    private String sourceRoutingKey;

    /** 业务幂等键：秒杀消息的 uniqueId，重放前要清掉对应的 Redis 标记 */
    private String bizKey;

    /** 消息原文，重放时原样投递 */
    private String body;

    /** 失败原因（预留，目前由日志承担） */
    private String errorMsg;

    /** 0待处理 1已重放 2已忽略 */
    private Integer status;

    /** 重放次数 */
    private Integer replayCount;

    private LocalDateTime createTime;

    /** 处理时间（重放或忽略的时间） */
    private LocalDateTime handleTime;
}
