package org.example.hotelreservation.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hotelreservation.config.RabbitMqConfig;
import org.example.hotelreservation.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费 close 队列：TTL + 死信到期后到达这里。
 * 作用：尝试关闭仍未支付的订单；已支付则在 OrderService 内幂等空转。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutListener {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMqConfig.CLOSE_QUEUE)
    public void onClose(String orderNo) {
        // ===== 延迟关单消息到达：委托状态机处理 =====
        log.info("timeout message received, orderNo={}", orderNo);
        orderService.closeIfUnpaid(orderNo);
    }
}
