package org.example.hotelreservation.enums;

import java.util.Set;

/**
 * 订单状态机。面试时要能画出转移图，并说明支付回调与超时关单的并发用 CAS 互斥。
 */
public enum OrderStatus {
    PENDING_PAY,
    CONFIRMED,
    CLOSED,
    CANCELLED,
    CHECKED_IN,
    COMPLETED;

    public boolean canTransitTo(OrderStatus next) {
        return allowedTargets().contains(next);
    }

    public Set<OrderStatus> allowedTargets() {
        return switch (this) {
            case PENDING_PAY -> Set.of(CONFIRMED, CLOSED, CANCELLED);
            case CONFIRMED -> Set.of(CANCELLED, CHECKED_IN);
            case CHECKED_IN -> Set.of(COMPLETED);
            case CLOSED, CANCELLED, COMPLETED -> Set.of();
        };
    }
}
