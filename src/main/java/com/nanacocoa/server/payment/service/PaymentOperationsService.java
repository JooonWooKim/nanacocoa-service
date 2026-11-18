package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.payment.dto.response.PaymentOperationResponse;
import com.nanacocoa.server.payment.pg.TossPaymentsClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Set;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class PaymentOperationsService {
    private static final Logger log = LoggerFactory.getLogger(PaymentOperationsService.class);
    private final PaymentOperationsQueryService queries;
    private final PaymentRecoveryService recovery;
    private final TossPaymentsClient provider;

    public PaymentOperationResponse.Page list(String kind, String orderId, String page, String size, UserDetailsImpl user) {
        queries.requireAdmin(user);
        if (kind != null && !Set.of("VERIFY_APPROVAL", "COMPENSATE_APPROVAL", "CANCELLATION").contains(kind)) invalid();
        if (orderId != null && (orderId.isBlank() || orderId.length() > 64)) invalid();
        return queries.list(kind, orderId, number(page, 0, Integer.MAX_VALUE), number(size, 1, 100));
    }

    public PaymentOperationResponse get(String type, String id, UserDetailsImpl user) {
        queries.requireAdmin(user);
        return queries.get(type(type), id(id));
    }

    // PG 호출 동안 DB 트랜잭션을 열어 두지 않는다.
    public PaymentOperationResponse reconcile(String type, String id, UserDetailsImpl user) {
        long operator = queries.requireAdmin(user);
        String validType = type(type);
        long validId = id(id);
        var before = queries.get(validType, validId);
        String result = "ERROR";
        try {
            if (provider.isDisabled()) throw new NanacocoaException(PAYMENT_PROVIDER_UNAVAILABLE);
            if (validType.equals("APPROVAL")) recovery.reconcileOneApproval(validId, before.orderId());
            else recovery.reconcileOneCancellation(validId, before.orderId());
            var after = queries.get(validType, validId);
            result = after.pending() ? "PENDING" : "COMPLETED";
            return after;
        } catch (NanacocoaException exception) {
            result = exception.getErrorCode().getCode();
            throw exception;
        } finally {
            log.info("Payment operation operatorId={} type={} id={} at={} result={}",
                    operator, validType, validId, LocalDateTime.now(), result);
        }
    }

    private String type(String type) {
        if (!"APPROVAL".equals(type) && !"CANCELLATION".equals(type)) invalid();
        return type;
    }
    private long id(String value) {
        try { long id = Long.parseLong(value); if (id > 0) return id; } catch (NumberFormatException ignored) {}
        throw new NanacocoaException(INVALID_REQUEST);
    }
    private int number(String value, int min, int max) {
        try { int n = Integer.parseInt(value); if (n >= min && n <= max) return n; } catch (NumberFormatException ignored) {}
        throw new NanacocoaException(INVALID_REQUEST);
    }
    private void invalid() { throw new NanacocoaException(INVALID_REQUEST); }
}
