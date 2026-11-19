package com.nanacocoa.server.order.dto.response;
import com.nanacocoa.server.order.entity.ReturnRequest;
import java.time.LocalDateTime;
import java.util.List;

public final class ReturnResponse {
    private ReturnResponse() {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Event(String action, String reason, Long operatorId, LocalDateTime recordedAt) {}
    public record Detail(Long id, String orderId, String type, String status, String reason, String policyVersion,
        String policyGuidance, LocalDateTime createdAt, long revision, Boolean resellable, Boolean refundEligible,
        String refundStatus, String restockStatus, Long cancellationId, List<String> actions, String holdReason,
        ShippingResponse.Tracking shipping, List<Event> history) {
        public boolean pending() { return "PENDING".equals(refundStatus); }
    }
    public record OrderView(List<Detail> requests, List<String> allowedTypes, String holdReason, String policyGuidance) {}
    public record Page(List<Detail> items, int page, boolean hasNext) {}
}
