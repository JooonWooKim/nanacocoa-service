package com.nanacocoa.server.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.order.config.ShippingCarriers;
import com.nanacocoa.server.order.dto.reqeust.ShippingChangeRequest;
import com.nanacocoa.server.order.dto.response.OrderItemResponse;
import com.nanacocoa.server.order.dto.response.ShippingResponse;
import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.entity.OrderStatus;
import com.nanacocoa.server.order.entity.Shipment;
import com.nanacocoa.server.order.entity.ShipmentEvent;
import com.nanacocoa.server.order.entity.ShippingStatus;
import com.nanacocoa.server.order.entity.ShipmentEvent.Action;
import com.nanacocoa.server.order.repository.OrderRepository;
import com.nanacocoa.server.order.repository.OrderRepository.ShippingSummary;
import com.nanacocoa.server.order.repository.ShipmentEventRepository;
import com.nanacocoa.server.order.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static com.nanacocoa.server.common.exception.ErrorCode.AUTHENTICATION_REQUIRED;
import static com.nanacocoa.server.common.exception.ErrorCode.IDEMPOTENCY_KEY_REUSED;
import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_REQUEST;
import static com.nanacocoa.server.common.exception.ErrorCode.NOT_FOUND_ORDER;
import static com.nanacocoa.server.common.exception.ErrorCode.SHIPPING_CANCEL_BLOCKED;
import static com.nanacocoa.server.common.exception.ErrorCode.SHIPPING_CONFLICT;

@Service
@RequiredArgsConstructor
public class ShippingService {
    private static final int PAGE_SIZE = 20;
    private final OrderRepository orders;
    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;
    private final MemberRepository members;
    private final ShippingCarriers carrierConfig;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ShippingResponse.Page list(String orderNumber, String state, int page, UserDetailsImpl user) {
        requireAdmin(user);
        if (page < 0 || page > Integer.MAX_VALUE / PAGE_SIZE
                || (orderNumber != null && orderNumber.length() > 64)) {
            throw new NanacocoaException(INVALID_REQUEST);
        }
        try {
            if (!"ALL".equals(state)) ShippingStatus.valueOf(state);
        } catch (IllegalArgumentException e) {
            throw new NanacocoaException(INVALID_REQUEST);
        }
        String search = orderNumber == null || orderNumber.isBlank() ? null : orderNumber.strip();
        var result = orders.findShippingOrders(search, state, PageRequest.of(page, PAGE_SIZE));
        return new ShippingResponse.Page(result.getContent(), page, result.hasNext());
    }

    @Transactional(readOnly = true)
    public ShippingResponse.Detail get(String orderNumber, UserDetailsImpl user) {
        requireAdmin(user);
        Order order = orders.findByOrderId(orderNumber).orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER));
        return detail(order, findShipment(order));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingResponse.Detail change(String orderNumber, ShippingChangeRequest request, UserDetailsImpl user) {
        Member admin = requireAdmin(user);
        Order order = orders.findByOrderIdForUpdate(orderNumber)
                .orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER));
        Shipment shipment = findShipment(order);
        String requestKey = request.requestKey().toString();
        String fingerprint = fingerprint(request);
        if (shipment != null) {
            var previous = events.findByShipmentIdAndRequestKey(shipment.getId(), requestKey);
            if (previous.isPresent()) {
                if (!previous.get().getFingerprint().equals(fingerprint)) {
                    throw new NanacocoaException(IDEMPOTENCY_KEY_REUSED);
                }
                return detail(order, shipment);
            }
        }
        if (request.revision() != (shipment == null ? 0 : shipment.getRevision())) {
            throw new NanacocoaException(SHIPPING_CONFLICT);
        }
        ShippingSummary summary = summary(order);
        Action action = request.action();
        if (!allowedActions(order, shipment, summary).contains(action)) {
            throw new NanacocoaException(SHIPPING_CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        validateChange(request, now);
        ShippingStatus previousStatus = ShippingStatus.valueOf(summary.getShippingStatus());
        if (shipment == null) shipment = new Shipment(order);
        String previousCarrier = shipment.getCarrierCode();
        String previousTracking = shipment.getTrackingNumber();

        switch (action) {
            case SHIP -> shipment.ship(request.carrierCode(), request.trackingNumber(), now);
            case CORRECT_TRACKING -> shipment.updateTracking(request.carrierCode(), request.trackingNumber());
            case DELIVER -> shipment.deliver(now);
            case IMPORT_SHIPPED -> shipment.ship(request.carrierCode(), request.trackingNumber(), request.shippedAt());
            case IMPORT_DELIVERED -> {
                shipment.ship(request.carrierCode(), request.trackingNumber(), request.shippedAt());
                shipment.deliver(request.deliveredAt());
            }
            case VERIFY_UNSHIPPED -> { /* A shipment record with no outbound status records verification. */ }
        }
        shipment.recordChange();
        shipments.saveAndFlush(shipment);
        ShippingStatus nextStatus = ShippingStatus.valueOf(summary(order).getShippingStatus());
        events.save(new ShipmentEvent(shipment, requestKey, fingerprint, action, previousStatus, nextStatus,
                previousCarrier, previousTracking, admin.getId(), now, request.reason().strip()));
        return detail(order, shipment);
    }

    @Transactional(readOnly = true)
    public ShippingResponse.Tracking tracking(Order order) {
        return tracking(findShipment(order), ShippingStatus.valueOf(summary(order).getShippingStatus()));
    }

    // Called after the payment flow has acquired the same order row lock used by change().
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkDirectCancellation(Order order) {
        Shipment shipment = findShipment(order);
        if (shipment == null ? order.isLegacyShippingReview() : shipment.getStatus() != null) {
            throw new NanacocoaException(SHIPPING_CANCEL_BLOCKED);
        }
    }

    private ShippingResponse.Detail detail(Order order, Shipment shipment) {
        ShippingSummary summary = summary(order);
        ShippingStatus status = ShippingStatus.valueOf(summary.getShippingStatus());
        var carriers = carrierConfig.getCarriers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ShippingResponse.CarrierOption(entry.getKey(), entry.getValue().name())).toList();
        return new ShippingResponse.Detail(summary, tracking(shipment, status),
                shipment == null ? 0 : shipment.getRevision(), shipment == null ? null : shipment.getCarrierCode(),
                order.getRecipientName(), order.getPhoneNumber(), order.getShippingAddress(), order.getCustomerRequest(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                holdReason(order, shipment, summary), allowedActions(order, shipment, summary), carriers,
                shipment == null ? List.of() : events.findByShipmentIdOrderByIdDesc(shipment.getId())
                        .stream().map(ShippingResponse.Event::from).toList());
    }

    private ShippingResponse.Tracking tracking(Shipment shipment, ShippingStatus status) {
        if (shipment == null || shipment.getStatus() == null) {
            return new ShippingResponse.Tracking(status, null, null, null, null, null);
        }
        var carrier = carrierConfig.getCarriers().get(shipment.getCarrierCode());
        return new ShippingResponse.Tracking(status, carrier == null ? shipment.getCarrierCode() : carrier.name(),
                shipment.getTrackingNumber(), carrier == null ? null : carrier.link(shipment.getTrackingNumber()),
                shipment.getShippedAt(), shipment.getDeliveredAt());
    }

    private List<Action> allowedActions(Order order, Shipment shipment, ShippingSummary summary) {
        ShippingStatus status = ShippingStatus.valueOf(summary.getShippingStatus());
        if (status == ShippingStatus.SHIPPED) return List.of(Action.CORRECT_TRACKING, Action.DELIVER);
        if (status == ShippingStatus.DELIVERED) return List.of(Action.CORRECT_TRACKING);
        if (status == ShippingStatus.PREPARING) return List.of(Action.SHIP);
        if (order.isLegacyShippingReview() && shipment == null
                && !"CANCELLATION_PENDING".equals(summary.getPaymentStatus())) {
            if (order.getStatus() == OrderStatus.PAID && "SUCCESS".equals(summary.getPaymentStatus())) {
                return List.of(Action.VERIFY_UNSHIPPED, Action.IMPORT_SHIPPED, Action.IMPORT_DELIVERED);
            }
            if (order.getStatus() == OrderStatus.CANCELED && "CANCELED".equals(summary.getPaymentStatus())) {
                return List.of(Action.IMPORT_SHIPPED, Action.IMPORT_DELIVERED);
            }
        }
        return List.of();
    }

    private String holdReason(Order order, Shipment shipment, ShippingSummary summary) {
        if (!ShippingStatus.ON_HOLD.name().equals(summary.getShippingStatus())) return null;
        if ("CANCELLATION_PENDING".equals(summary.getPaymentStatus())) return "결제 취소 결과를 다시 확인해 주세요.";
        if (order.isLegacyShippingReview() && shipment == null) return "과거 주문의 실제 출고 기록을 대조해 주세요.";
        return "결제·재고 확정과 주문 당시 배송 정보를 확인해 주세요.";
    }

    private void validateChange(ShippingChangeRequest request, LocalDateTime now) {
        switch (request.action()) {
            case SHIP, CORRECT_TRACKING, IMPORT_SHIPPED, IMPORT_DELIVERED -> {
                String code = request.carrierCode();
                var carrier = code == null ? null : carrierConfig.getCarriers().get(code);
                if (carrier == null || request.trackingNumber() == null || !carrier.accepts(request.trackingNumber())) {
                    throw new NanacocoaException(INVALID_REQUEST);
                }
            }
            case DELIVER, VERIFY_UNSHIPPED -> {
                if (request.carrierCode() != null || request.trackingNumber() != null) {
                    throw new NanacocoaException(INVALID_REQUEST);
                }
            }
        }
        boolean imported = request.action() == Action.IMPORT_SHIPPED || request.action() == Action.IMPORT_DELIVERED;
        LocalDateTime shipped = request.shippedAt();
        LocalDateTime delivered = request.deliveredAt();
        if (imported != (shipped != null) || (shipped != null && shipped.isAfter(now))
                || (request.action() == Action.IMPORT_DELIVERED) != (delivered != null)
                || (delivered != null && (delivered.isBefore(shipped) || delivered.isAfter(now)))) {
            throw new NanacocoaException(INVALID_REQUEST);
        }
    }

    private ShippingSummary summary(Order order) {
        return orders.findShippingSummary(order.getOrderId()).orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER));
    }

    private Shipment findShipment(Order order) {
        return shipments.findByOrderId(order.getId()).orElse(null);
    }

    private Member requireAdmin(UserDetailsImpl user) {
        if (user == null) throw new NanacocoaException(AUTHENTICATION_REQUIRED);
        Member member = members.findByEmail(user.getUsername())
                .orElseThrow(() -> new NanacocoaException(AUTHENTICATION_REQUIRED));
        member.checkAdmin();
        return member;
    }

    private String fingerprint(ShippingChangeRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(request)));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Cannot fingerprint shipping request", e);
        }
    }
}
