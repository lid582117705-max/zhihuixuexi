package com.boxue.module.order.service;

import com.boxue.common.auth.UserContext;
import com.boxue.common.exception.AuthException;
import com.boxue.common.exception.BizException;
import com.boxue.module.course.entity.Course;
import com.boxue.module.course.service.CourseService;
import com.boxue.module.order.dto.CreateOrderRequest;
import com.boxue.module.order.entity.Order;
import com.boxue.module.order.mapper.OrderMapper;
import com.boxue.module.user.entity.User;
import com.boxue.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final CourseService courseService;

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
        return order;
    }

    public Order getById(Long id) {
        Long userId = requireLoginUserId();
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
