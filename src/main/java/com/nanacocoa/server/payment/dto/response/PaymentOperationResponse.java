package com.nanacocoa.server.payment.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record PaymentOperationResponse(
        String type, long id, long paymentId, String orderId, String kind, long amount,
        String status, String paymentStatus, String orderStatus, String stockState,
        LocalDateTime createdAt, LocalDateTime updatedAt, int retryCount, LocalDateTime nextRetryAt,
        String errorCategory, String errorMessage, boolean pending, boolean canReconcile, String holdReason) {
    public record Page(List<PaymentOperationResponse> items, int page, int size, long totalElements, boolean hasNext) {}
}
