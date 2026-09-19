package com.boxue.module.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的课程：一条已支付订单 + 对应课程信息（由 OrderService 组装）
 */
@Data
public class MineCourseVO {

    private Long orderId;
    private Long courseId;
    private String courseTitle;
    private String teacherName;
    private String cover;
    /** 实付金额（下单时的课程价格） */
    private BigDecimal amount;
    /** 购买（支付成功）时间 */
    private LocalDateTime payTime;
}