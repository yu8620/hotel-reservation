package org.example.hotelreservation.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hotelreservation.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MQ 丢消息或进程重启时的兜底。面试要主动说：延迟队列是主路径，定时扫描是补偿。
 */
@Slf4j
@Component
@RequiredArgsConstructor
/**
 * 定时补偿：扫描过期未支付订单；MQ 为主路径，本类为兜底。
 */
public class OrderTimeoutScheduler {

    private final OrderService orderService;

    @Scheduled(fixedDelay = 30_000)
    public void closeExpired() {
        int n = orderService.closeExpiredOrders();
        if (n > 0) {
            log.info("scheduler closed {} unpaid orders", n);
        }
    }
}
