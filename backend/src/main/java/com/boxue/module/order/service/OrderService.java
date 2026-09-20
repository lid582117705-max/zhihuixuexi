package com.boxue.module.order.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.boxue.common.auth.UserContext;
import com.boxue.common.exception.AuthException;
import com.boxue.common.exception.BizException;
import com.boxue.config.RabbitConfig;
import com.boxue.module.course.entity.Course;
import com.boxue.module.course.service.CourseService;
import com.boxue.module.course.mapper.CourseMapper;
import com.boxue.module.order.dto.MineCourseVO;
import com.boxue.module.order.dto.CreateOrderRequest;
import com.boxue.module.order.entity.Order;
import com.boxue.module.order.mapper.OrderMapper;
import com.boxue.module.user.entity.User;
import com.boxue.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final CourseService courseService;
    private final CourseMapper courseMapper;
    private final RabbitTemplate rabbitTemplate;

    @Transactional(rollbackFor = Exception.class)
    public Order create(CreateOrderRequest request) {
        Long userId = requireLoginUserId();
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("用户不存在或已禁用");
        }

        Course course = courseService.getById(request.getCourseId());

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(user.getId());
        order.setCourseId(course.getId());
        order.setAmount(course.getPrice());
        order.setStatus(0);
        orderMapper.insert(order);

        // 下单成功 → 往延迟队列发一条消息（内容是订单 id），到点由消费者检查是否自动关单
        // correlationId 用来关联"生产者确认"回调：broker 收到后会带着这个 id 回 ack/nack
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        log.info("[下单] 订单 {} 已创建，发送延迟关单消息，correlationId={}",
                order.getOrderNo(), correlationData.getId());
        // 显式传 4 参数版本避免重载歧义：exchange 用默认交换机、routingKey 用队列名
        rabbitTemplate.convertAndSend("", RabbitConfig.DELAY_QUEUE, String.valueOf(order.getId()), correlationData);

        return order;
    }

    /**
     * 模拟支付成功（真实项目里这里是支付平台回调通知）
     */
    @Transactional(rollbackFor = Exception.class)
    public Order pay(Long orderId) {
        Long userId = requireLoginUserId();
        Order order = getOwnOrder(orderId, userId);

        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new BizException("当前订单状态不可支付");
        }

        Order update = new Order();
        update.setId(orderId);
        update.setStatus(1);
        update.setPayTime(LocalDateTime.now());
        orderMapper.updateById(update);

        log.info("[支付] 订单 {} 支付成功", order.getOrderNo());
        return orderMapper.selectById(orderId);
    }

    /**
     * 延迟消息到期后调用：只有订单仍是"待支付"才关闭（避免误关已支付订单）
     */
    public void closeIfUnpaid(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("[关单] 订单 {} 不存在，忽略", orderId);
            return;
        }

        // 原子更新：UPDATE orders SET status=2, cancel_time=now WHERE id=? AND status=0
        // 只有"仍旧待支付"的行才会被更新，已支付/已取消的订单行不会动
        UpdateWrapper<Order> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", orderId).eq("status", 0);

        Order update = new Order();
        update.setStatus(2);
        update.setCancelTime(LocalDateTime.now());

        int rows = orderMapper.update(update, wrapper);
        if (rows == 1) {
            log.info("[关单] 订单 {} 超时未支付，已自动关闭", order.getOrderNo());
        } else {
            log.info("[关单] 订单 {} 当前状态={}，跳过关闭", order.getOrderNo(), order.getStatus());
        }
    }

    /**
     * 我的课程：查当前用户全部"已支付"订单，批量查出课程信息后组装返回。
     * 用 selectBatchIds 一次查完所有课程，避免 for 循环里单查（N+1 问题）。
     */
    public List<MineCourseVO> mine() {
        Long userId = requireLoginUserId();

        // 第 1 步：查我所有已支付的订单（按支付时间倒序）
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, 1)
                .orderByDesc(Order::getPayTime));
        if (orders.isEmpty()) {
            return new ArrayList<>();
        }

        // 第 2 步：批量查课程，转成 id -> Course 的 Map
        List<Long> courseIds = orders.stream()
                .map(Order::getCourseId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Course> courseMap = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        // 第 3 步：组装 VO
        return orders.stream().map(order -> {
            Course course = courseMap.get(order.getCourseId());
            MineCourseVO vo = new MineCourseVO();
            vo.setOrderId(order.getId());
            vo.setCourseId(order.getCourseId());
            vo.setCourseTitle(course != null ? course.getTitle() : "课程已下架");
            vo.setTeacherName(course != null ? course.getTeacherName() : null);
            vo.setCover(course != null ? course.getCover() : null);
            vo.setAmount(order.getAmount());
            vo.setPayTime(order.getPayTime());
            return vo;
        }).collect(Collectors.toList());
    }
    public Order getById(Long id) {
        Long userId = requireLoginUserId();
        return getOwnOrder(id, userId);
    }

    private Order getOwnOrder(Long id, Long userId) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!userId.equals(order.getUserId())) {
            throw new BizException("无权查看该订单");
        }
        return order;
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthException("请先登录");
        }
        return userId;
    }

    private String generateOrderNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "BX" + time + random;
    }
}