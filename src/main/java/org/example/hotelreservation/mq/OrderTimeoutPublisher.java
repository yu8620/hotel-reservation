package org.example.hotelreservation.mq;

import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.config.HotelProperties;
import org.example.hotelreservation.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/**
 * 下单后投递延迟关单消息（消息 TTL = 支付超时时间）。
 */
public class OrderTimeoutPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final HotelProperties properties;

    public void sendDelayClose(String orderNo) {
        long ttl = properties.getOrder().getPayTimeoutMinutes() * 60_000L;
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.DELAY_RK, orderNo, message -> {
            message.getMessageProperties().setExpiration(String.valueOf(ttl));
            return message;
        });
    }
}
