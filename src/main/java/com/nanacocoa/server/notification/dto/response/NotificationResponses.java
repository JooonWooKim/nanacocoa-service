package com.nanacocoa.server.notification.dto.response;

import com.nanacocoa.server.notification.entity.OrderNotification;
import com.nanacocoa.server.notification.entity.OrderNotification.Status;
import com.nanacocoa.server.notification.entity.OrderNotification.Type;
import java.time.LocalDateTime;
import java.util.List;

public final class NotificationResponses {
    private NotificationResponses() {}

    public record Item(Long id, String orderId, Type type, LocalDateTime confirmedAt, LocalDateTime readAt) {
        public static Item from(OrderNotification row) {
            return new Item(row.getId(), row.getOrderNumber(), row.getEventType(), row.getConfirmedAt(), row.getReadAt());
        }
    }
    public record Page(List<Item> items, int page, boolean hasNext, long unreadCount) {}
    public record Operation(Long id, String orderId, Type type, Status status, int attemptCount,
                            String failureCode, LocalDateTime failedAt) {
        public static Operation from(OrderNotification row) {
            return new Operation(row.getId(), row.getOrderNumber(), row.getEventType(), row.getStatus(),
                    row.getAttemptCount(), row.getFailureCode(), row.getFailedAt());
        }
    }
    public record OperationsPage(List<Operation> items, int page, boolean hasNext) {}
}
