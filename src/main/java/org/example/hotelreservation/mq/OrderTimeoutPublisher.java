package org.example.hotelreservation.mq;

import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.config.HotelProperties;
import org.example.hotelreservation.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 下单成功后投递「延迟关单」消息。
 * 实现方式：发到 delay 队列并设置消息 TTL；过期后经 DLX 进入 close 队列。
 */
@Component
@RequiredArgsConstructor
public class OrderTimeoutPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final HotelProperties properties;

    public void sendDelayClose(String orderNo) {
        // ===== TTL = 支付超时分钟数，到期后死信到 close 队列 =====
        long ttl = properties.getOrder().getPayTimeoutMinutes() * 60_000L;
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.DELAY_RK, orderNo, message -> {
            message.getMessageProperties().setExpiration(String.valueOf(ttl));
            return message;
        });
    }
}
