package org.example.hotelreservation.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hotelreservation.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付超时补偿扫描。
 * <p>面试口径：RabbitMQ TTL+DLX 是主路径；本定时任务防 MQ 丢消息/进程重启漏关单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderService orderService;

    @Scheduled(fixedDelay = 30_000)
    public void closeExpired() {
        // ===== 扫描 expireTime 已过且仍待支付的订单并关单回补 =====
        int n = orderService.closeExpiredOrders();
        if (n > 0) {
            log.info("scheduler closed {} unpaid orders", n);
        }
    }
}
