package com.nanacocoa.server.order.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OrderReservationTest {
    @Test
    void reservationUsesDatabasePrecisionAtExpiryBoundary() {
        Order order = Order.create("precision-test", null, "테스트", "010-0000-0000", "테스트 배송지", null);
        LocalDateTime originalExpiry = LocalDateTime.parse("2026-11-24T02:41:18.674396837");
        LocalDateTime storedExpiry = LocalDateTime.parse("2026-11-24T02:41:18.674396");

        order.reserveStock(originalExpiry);

        assertThat(order.getReservationExpiresAt()).isEqualTo(storedExpiry);
        assertThat(order.reservationExpired(storedExpiry.minusNanos(1))).isFalse();
        assertThat(order.reservationExpired(storedExpiry)).isTrue();
    }
}
