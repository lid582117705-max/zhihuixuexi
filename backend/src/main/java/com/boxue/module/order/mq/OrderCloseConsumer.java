package com.boxue.module.order.mq;

import com.boxue.config.RabbitConfig;
import com.boxue.module.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单关单消费者：收到延迟消息（内容为订单 id）后，
 * 检查订单是否仍是"待支付"，是则自动关闭为"已取消"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitConfig.CLOSE_QUEUE)
    public void onCloseMessage(String orderIdStr) {
        log.info("[消费者] 收到延迟关单消息，订单 id = {}", orderIdStr);
        orderService.closeIfUnpaid(Long.valueOf(orderIdStr));
    }
}