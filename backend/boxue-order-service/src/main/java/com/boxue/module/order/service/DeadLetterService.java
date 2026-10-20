package com.boxue.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.boxue.common.exception.BizException;
import com.boxue.config.RabbitConfig;
import com.boxue.module.order.entity.DeadLetter;
import com.boxue.module.order.mapper.DeadLetterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 死信处理服务：落库 / 查询 / 重放 / 忽略
 *
 * 重放的两个关键点：
 * 1. 投回【原队列】而不是原交换机 —— 消息经过 business.dlx 后，receivedExchange 已经变成
 *    死信交换机了，原路由信息丢失；但 x-death 头里记着"死前所在的队列"，投回队列最稳。
 * 2. 重放前必须清幂等标记 —— 秒杀消费者用 Redis SETNX 按 uniqueId 去重，
 *    不清掉的话重放的消息会被当成"已处理"直接跳过，等于白放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    /** 秒杀消费者的幂等 key 前缀（和 SeckillOrderConsumer 保持一致） */
    private static final String SECKILL_CONSUME_PREFIX = "seckill:consume:";

    private final DeadLetterMapper deadLetterMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;

    /** 死信落库（由 DeadLetterConsumer 调用） */
    public void save(DeadLetter record) {
        if (record.getStatus() == null) {
            record.setStatus(0);
        }
        if (record.getReplayCount() == null) {
            record.setReplayCount(0);
        }
        if (record.getCreateTime() == null) {
            record.setCreateTime(LocalDateTime.now());
        }
        deadLetterMapper.insert(record);
    }

    /**
     * 死信列表：默认最近 100 条，可按状态过滤
     * status: 0待处理 1已重放 2已忽略
     */
    public List<DeadLetter> list(Integer status) {
        LambdaQueryWrapper<DeadLetter> wrapper = new LambdaQueryWrapper<DeadLetter>()
                // eq 的第一个参数是"条件生效开关"，status 为 null 时不拼这个条件
                .eq(status != null, DeadLetter::getStatus, status)
                .orderByDesc(DeadLetter::getId)
                .last("LIMIT 100");
        return deadLetterMapper.selectList(wrapper);
    }

    /**
     * 重放单条死信：投回原队列 + 清幂等标记 + 更新状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void replay(Long id) {
        DeadLetter record = deadLetterMapper.selectById(id);
        if (record == null) {
            throw new BizException("死信记录不存在");
        }
        // 已忽略的是"人工确认过不用处理"的，不允许再重放
        if (record.getStatus() != null && record.getStatus() == 2) {
            throw new BizException("该记录已标记为忽略，不能重放");
        }

        // 第 1 步：清幂等标记
        if (StringUtils.hasText(record.getBizKey())) {
            String key = SECKILL_CONSUME_PREFIX + record.getBizKey();
            redisTemplate.delete(key);
            log.info("[死信重放] 已清理幂等标记 key={}", key);
        }

        // 第 2 步：投回原队列。走默认交换机，路由键 = 队列名
        String targetQueue = StringUtils.hasText(record.getSourceQueue())
                ? record.getSourceQueue()
                : RabbitConfig.CLOSE_QUEUE;
        rabbitTemplate.convertAndSend("", targetQueue, record.getBody(),
                new CorrelationData(UUID.randomUUID().toString()));
        log.info("[死信重放] id={} 已投回队列 {}，body={}", id, targetQueue, record.getBody());

        // 第 3 步：更新记录
        record.setStatus(1);
        record.setReplayCount(record.getReplayCount() == null ? 1 : record.getReplayCount() + 1);
        record.setHandleTime(LocalDateTime.now());
        deadLetterMapper.updateById(record);
    }

    /**
     * 标记忽略：脏数据（比如订单 id 根本不存在）重放多少次都没用，
     * 人工确认后归档，避免列表里一直堆着待处理
     */
    public void ignore(Long id) {
        DeadLetter record = deadLetterMapper.selectById(id);
        if (record == null) {
            throw new BizException("死信记录不存在");
        }
        record.setStatus(2);
        record.setHandleTime(LocalDateTime.now());
        deadLetterMapper.updateById(record);
        log.info("[死信忽略] id={} 已标记忽略", id);
    }
}
