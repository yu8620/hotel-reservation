package org.example.hotelreservation.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {

    @Test
    void pendingPayCanPayCloseOrCancel() {
        assertTrue(OrderStatus.PENDING_PAY.canTransitTo(OrderStatus.CONFIRMED));
        assertTrue(OrderStatus.PENDING_PAY.canTransitTo(OrderStatus.CLOSED));
        assertTrue(OrderStatus.PENDING_PAY.canTransitTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.PENDING_PAY.canTransitTo(OrderStatus.COMPLETED));
    }

    @Test
    void confirmedCannotPayAgain() {
        assertFalse(OrderStatus.CONFIRMED.canTransitTo(OrderStatus.CONFIRMED));
        assertTrue(OrderStatus.CONFIRMED.canTransitTo(OrderStatus.CANCELLED));
        assertTrue(OrderStatus.CONFIRMED.canTransitTo(OrderStatus.CHECKED_IN));
    }

    @Test
    void terminalStatesAreFrozen() {
        assertTrue(OrderStatus.CLOSED.allowedTargets().isEmpty());
        assertTrue(OrderStatus.CANCELLED.allowedTargets().isEmpty());
        assertTrue(OrderStatus.COMPLETED.allowedTargets().isEmpty());
    }
}
