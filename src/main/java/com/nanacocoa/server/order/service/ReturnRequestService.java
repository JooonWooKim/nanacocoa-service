package com.nanacocoa.server.order.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.order.config.ReturnPolicy;
import com.nanacocoa.server.order.dto.reqeust.CreateReturnRequest;
import com.nanacocoa.server.order.dto.reqeust.ReturnActionRequest;
import com.nanacocoa.server.order.dto.response.ReturnResponse;
import com.nanacocoa.server.order.entity.*;
import com.nanacocoa.server.order.repository.*;
import com.nanacocoa.server.payment.entity.*;
import com.nanacocoa.server.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReturnRequestService {
    private final ReturnRequestRepository requests;
    private final ReturnRequestEventRepository events;
    private final OrderRepository orders;
    private final MemberRepository members;
    private final PaymentRepository payments;
    private final ShipmentRepository shipments;
    private final ShippingService shipping;
    private final OrderInventoryService inventory;
    private final ReturnPolicy policy;

    public Member member(UserDetailsImpl user, boolean admin) {
        if (user == null) throw new NanacocoaException(AUTHENTICATION_REQUIRED);
        Member member = members.findByEmail(user.getUsername()).orElseThrow(() -> new NanacocoaException(AUTHENTICATION_REQUIRED));
        if (admin) member.checkAdmin();
        return member;
    }
    private Order owned(String orderId, UserDetailsImpl user) {
        Member member = member(user, false);
        return orders.findByOrderIdAndMemberId(orderId, member.getId()).orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER));
    }
    public ReturnResponse.OrderView ownedView(String orderId, UserDetailsImpl user) { return orderView(owned(orderId, user)); }

    public ReturnResponse.OrderView orderView(Order order) {
        var rows = requests.findByOrderIdOrderByIdDesc(order.getId());
        List<String> types = eligibleTypes(order);
        if (requests.findByActiveOrderId(order.getId()).isPresent()
                || (!rows.isEmpty() && !Boolean.TRUE.equals(policy.getAllowReapply()))) types = List.of();
        return new ReturnResponse.OrderView(rows.stream().map(r -> detail(r, false)).toList(), types,
            types.isEmpty() ? (!policy.available() ? "취소·반품 정책 승인 및 운영 준비 중입니다."
                : "현재 주문 상태에서는 새 신청을 할 수 없습니다. 신청 내역을 확인해주세요.") : null,
            policy.available() ? policy.getGuidance() : null);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReturnResponse.OrderView create(String orderId, CreateReturnRequest input, UserDetailsImpl user) {
        Member member = member(user, false);
        Order order = orders.findByOrderIdForUpdate(orderId).orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER));
        if (!order.isOwnedBy(member.getEmail())) throw new NanacocoaException(NOT_FOUND_ORDER);
        if (input == null || input.requestKey() == null || input.type() == null) invalid();
        String reason = reason(input.reason());
        String fingerprint = fingerprint(input.type().name(), reason);
        var old = requests.findByOrderIdAndRequestKey(order.getId(), input.requestKey().toString());
        if (old.isPresent()) {
            if (!old.get().getFingerprint().equals(fingerprint)) conflict();
            return orderView(order);
        }
        policy.requireAvailable();
        if (!orderView(order).allowedTypes().contains(input.type().name())) conflict();
        requests.saveAndFlush(new ReturnRequest(order, input.requestKey().toString(), fingerprint,
            input.type(), reason, policy.getVersion(), policy.getGuidance()));
        return orderView(order);
    }
    private List<String> eligibleTypes(Order order) {
        if (!policy.available() || order.getStatus() != OrderStatus.PAID || order.getStockState() != StockState.SOLD
            || !payments.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)
            || payments.existsByOrderIdAndStatus(order.getId(), PaymentStatus.CANCEL_PENDING)) return List.of();
        Shipment shipment = shipments.findByOrderId(order.getId()).orElse(null);
        if (shipment == null || shipment.getStatus() == null)
            return order.isLegacyShippingReview() && shipment == null ? List.of() : List.of("CANCELLATION");
        LocalDateTime anchor = shipment.getDeliveredAt();
        if (anchor == null) {
            if (!Boolean.TRUE.equals(policy.getAllowInTransit())) return List.of();
            anchor = shipment.getShippedAt();
        }
        LocalDateTime now = LocalDateTime.now();
        return anchor != null && !anchor.isAfter(now) && !now.isAfter(anchor.plusDays(policy.getWindowDays()))
            ? List.of("RETURN") : List.of();
    }
    public ReturnResponse.Page list(String orderId, String type, String status, int page, UserDetailsImpl user) {
        member(user, true);
        if (page < 0 || page > Integer.MAX_VALUE / 20 || (orderId != null && (orderId.isBlank() || orderId.length() > 64))) invalid();
        try {
            var result = requests.search(orderId, type == null ? null : ReturnRequest.Type.valueOf(type),
                status == null ? null : ReturnRequest.Status.valueOf(status), PageRequest.of(page, 20));
            return new ReturnResponse.Page(result.stream().map(r -> detail(r, true)).toList(), page, result.hasNext());
        } catch (IllegalArgumentException e) { throw new NanacocoaException(INVALID_REQUEST); }
    }
    public ReturnResponse.Detail get(long id, UserDetailsImpl user) {
        member(user, true); return detail(request(id), true);
    }
    public RefundTarget refundTarget(long id, UserDetailsImpl user) {
        member(user, true);
        ReturnRequest request = request(id);
        Payment payment = payments.findFirstByOrderIdAndStatusOrderByIdDesc(request.getOrder().getId(), PaymentStatus.SUCCESS)
            .orElseGet(() -> request.getCancellation() == null ? null : request.getCancellation().getPayment());
        if (payment == null) conflict();
        return new RefundTarget(request.getOrder().getOrderId(), payment.getId());
    }
    public record RefundTarget(String orderId, Long paymentId) {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReturnResponse.Detail change(long id, ReturnActionRequest input, UserDetailsImpl user) {
        member(user, true);
        if (input == null || input.action() == ReturnRequest.Action.REFUND) invalid();
        String orderNumber = requests.findOrderNumber(id).orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER));
        orders.findByOrderIdForUpdate(orderNumber).orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER));
        return changeLocked(id, input, user);
    }
    private ReturnResponse.Detail changeLocked(long id, ReturnActionRequest input, UserDetailsImpl user) {
        var intent = recordAction(id, input, user);
        if (!intent.replay() && input.action() == ReturnRequest.Action.RESTOCK)
            inventory.restock(intent.request().getOrder(), reason(input.reason()));
        return detail(intent.request(), true);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public RefundIntent authorizeRefund(long id, ReturnActionRequest input, UserDetailsImpl user, Payment payment) {
        if (input == null || input.action() != ReturnRequest.Action.REFUND) invalid();
        var intent = recordAction(id, input, user);
        if (!Objects.equals(intent.request().getOrder().getId(), payment.getOrder().getId())) conflict();
        return intent;
    }
    // Callers hold the order row lock. Never acquire a payment lock from this boundary.
    private RefundIntent recordAction(long id, ReturnActionRequest input, UserDetailsImpl user) {
        Member operator = member(user, true);
        if (input == null || input.requestKey() == null || input.action() == null || input.revision() == null || input.revision() < 0) invalid();
        ReturnRequest request = request(id);
        String fingerprint = fingerprint(input.action().name(), String.valueOf(input.revision()), reason(input.reason()),
            String.valueOf(input.resellable()), String.valueOf(input.refundEligible()), operator.getId().toString());
        var previous = events.findByRequestIdAndRequestKey(id, input.requestKey().toString());
        if (previous.isPresent()) {
            if (!previous.get().getFingerprint().equals(fingerprint)) conflict();
            return new RefundIntent(request, previous.get(), true);
        }
        policy.requireAvailable();
        if (!policy.getVersion().equals(request.getPolicyVersion()) || request.getRevision() != input.revision()
                || !actions(request).contains(input.action().name())) conflict();
        if (input.action() == ReturnRequest.Action.INSPECT) {
            if (input.resellable() == null || input.refundEligible() == null) invalid();
            if (input.refundEligible() && !input.resellable() && !Boolean.TRUE.equals(policy.getRefundAfterFailedInspection())) conflict();
        } else if (input.resellable() != null || input.refundEligible() != null) invalid();
        request.change(input.action(), input.resellable(), input.refundEligible());
        var event = events.saveAndFlush(new ReturnRequestEvent(request, input.requestKey().toString(), fingerprint,
            input.action(), operator.getId(), reason(input.reason())));
        return new RefundIntent(request, event, false);
    }
    public record RefundIntent(ReturnRequest request, ReturnRequestEvent event, boolean replay) {}

    private List<String> actions(ReturnRequest r) {
        if (!policy.available() || !policy.getVersion().equals(r.getPolicyVersion())) return List.of();
        var order = r.getOrder();
        var cancellation = r.getCancellation();
        if (cancellation != null && cancellation.getStatus() == CancellationStatus.PENDING) return List.of();
        if (cancellation != null && cancellation.getStatus() == CancellationStatus.SUCCESS)
            return r.getStatus() == ReturnRequest.Status.INSPECTED && Boolean.TRUE.equals(r.getResellable())
                && order.getStatus() == OrderStatus.CANCELED && order.getStockState() == StockState.SOLD
                ? List.of("RESTOCK") : List.of();
        if (order.getStatus() != OrderStatus.PAID || order.getStockState() != StockState.SOLD
                || !payments.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)) return List.of();
        return switch (r.getStatus()) {
            case REQUESTED -> List.of("APPROVE", "REJECT");
            case APPROVED -> r.getType() == ReturnRequest.Type.CANCELLATION
                ? List.of("INSPECT", "REJECT") : List.of("COLLECT", "REJECT");
            case COLLECTING -> List.of("RECEIVE");
            case RECEIVED -> List.of("INSPECT");
            case INSPECTED -> Boolean.TRUE.equals(r.getRefundEligible()) ? List.of("REFUND") : List.of("REJECT");
            case REJECTED -> List.of();
        };
    }
    private ReturnResponse.Detail detail(ReturnRequest r, boolean admin) {
        var c = r.getCancellation();
        String refund = c == null ? "NOT_STARTED" : c.getStatus().name();
        String restock = c != null && c.getStatus() == CancellationStatus.SUCCESS
            && r.getOrder().getStockState() == StockState.RETURNED ? "COMPLETED"
            : Boolean.FALSE.equals(r.getResellable()) ? "FAILED_INSPECTION" : "NOT_STARTED";
        List<String> actions = admin ? actions(r) : List.of();
        var history = events.findByRequestIdOrderByIdAsc(r.getId()).stream()
            .filter(e -> admin || e.getAction() != ReturnRequest.Action.RESTOCK)
            .map(e -> new ReturnResponse.Event(e.getAction().name(),
                admin || e.getAction() == ReturnRequest.Action.REJECT ? e.getReason() : null,
                admin ? e.getOperatorId() : null, e.getRecordedAt())).toList();
        return new ReturnResponse.Detail(r.getId(), r.getOrder().getOrderId(), r.getType().name(), r.getStatus().name(),
            r.getReason(), r.getPolicyVersion(), r.getPolicyGuidance(), r.getCreatedAt(), r.getRevision(),
            r.getResellable(), r.getRefundEligible(), refund, restock, admin && c != null ? c.getId() : null,
            actions, !policy.available() ? "취소·반품 정책 승인 및 운영 준비 중입니다."
                : !policy.getVersion().equals(r.getPolicyVersion()) ? "접수 당시 정책과 운영 설정의 버전을 확인해주세요." : null,
            shipping.tracking(r.getOrder()), history);
    }
    private ReturnRequest request(long id) {
        if (id <= 0) invalid();
        return requests.findById(id).orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER));
    }
    private String reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) invalid();
        return reason.strip();
    }
    private String fingerprint(String... values) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void invalid() { throw new NanacocoaException(INVALID_REQUEST); }
    private void conflict() { throw new NanacocoaException(RETURN_CONFLICT); }
}
