package com.nanacocoa.server.order.dto.response;

import com.nanacocoa.server.order.entity.ShipmentEvent;
import com.nanacocoa.server.order.entity.ShipmentEvent.Action;
import com.nanacocoa.server.order.entity.ShippingStatus;
import com.nanacocoa.server.order.repository.OrderRepository.ShippingSummary;

import java.time.LocalDateTime;
import java.util.List;

public final class ShippingResponse {
    private ShippingResponse() {}

    public record Tracking(ShippingStatus status, String carrierName, String trackingNumber,
                           String trackingUrl, LocalDateTime shippedAt, LocalDateTime deliveredAt) {}

    public record Page(List<ShippingSummary> orders, int page, boolean hasNext) {}

    public record CarrierOption(String code, String name) {}

    public record Detail(ShippingSummary order, Tracking tracking, long revision, String carrierCode,
                         String recipientName, String phoneNumber, String shippingAddress, String customerRequest,
                         List<OrderItemResponse> items, String holdReason, List<Action> actions,
                         List<CarrierOption> carriers, List<Event> history) {}

    public record Event(Action action, ShippingStatus previousStatus, ShippingStatus nextStatus,
                        String previousCarrier, String previousTracking, String nextCarrier, String nextTracking,
                        Long operatorId, LocalDateTime recordedAt, String reason) {
        public static Event from(ShipmentEvent event) {
            return new Event(event.getAction(), event.getPreviousStatus(), event.getNextStatus(),
                    event.getPreviousCarrier(), event.getPreviousTracking(), event.getNextCarrier(),
                    event.getNextTracking(), event.getOperatorId(), event.getRecordedAt(), event.getReason());
        }
    }
}
