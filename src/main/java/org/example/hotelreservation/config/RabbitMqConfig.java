package org.example.hotelreservation.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 未支付订单超时拓扑：delay 队列（消息 TTL）→ 死信 → close 队列。
 * <p>不依赖 rabbitmq_delayed_message 插件，面试可画：业务交换机 + TTL + DLX。
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "hotel.order.exchange";
    public static final String DELAY_QUEUE = "hotel.order.delay.queue";
    public static final String CLOSE_QUEUE = "hotel.order.close.queue";
    public static final String DELAY_RK = "order.delay";
    public static final String CLOSE_RK = "order.close";

    @Bean
    public DirectExchange orderExchange() {
        // ===== 持久化直连交换机，delay/close 共用 =====
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue delayQueue() {
        // ===== 延迟队列：消息过期后死信到 CLOSE_RK =====
        return QueueBuilder.durable(DELAY_QUEUE)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(CLOSE_RK)
                .build();
    }

    @Bean
    public Queue closeQueue() {
        // ===== 真正被 Listener 消费的关单队列 =====
        return QueueBuilder.durable(CLOSE_QUEUE).build();
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue()).to(orderExchange()).with(DELAY_RK);
    }

    @Bean
    public Binding closeBinding() {
        return BindingBuilder.bind(closeQueue()).to(orderExchange()).with(CLOSE_RK);
    }
}
