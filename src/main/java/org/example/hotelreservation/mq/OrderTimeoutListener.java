package org.example.hotelreservation.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hotelreservation.config.RabbitMqConfig;
import org.example.hotelreservation.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutListener {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMqConfig.CLOSE_QUEUE)
    public void onClose(String orderNo) {
        log.info("timeout message received, orderNo={}", orderNo);
        orderService.closeIfUnpaid(orderNo);
    }
}
