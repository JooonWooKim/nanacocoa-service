package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.payment.repository.PaymentRepository;
import com.nanacocoa.server.payment.repository.PaymentRepository.OperationRow;
import com.nanacocoa.server.payment.dto.response.PaymentOperationResponse;
import com.nanacocoa.server.payment.pg.TossPaymentsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentOperationsQueryService {
    private final MemberRepository members;
    private final PaymentRepository payments;
    private final TossPaymentsClient provider;

    public long requireAdmin(UserDetailsImpl user) {
        if (user == null) throw new NanacocoaException(AUTHENTICATION_REQUIRED);
        var member = members.findByEmail(user.getUsername())
                .orElseThrow(() -> new NanacocoaException(AUTHENTICATION_REQUIRED));
        member.checkAdmin();
        return member.getId();
    }

    public PaymentOperationResponse.Page list(String kind, String orderId, int page, int size) {
        long offset = (long) page * size;
        var items = payments.findOperations(kind, orderId, size, offset).stream().map(this::map).toList();
        long total = payments.countOperations(kind, orderId);
        return new PaymentOperationResponse.Page(items, page, size, total, offset + items.size() < total);
    }

    public PaymentOperationResponse get(String type, long id) {
        return payments.findOperation(type, id).map(this::map)
                .orElseThrow(() -> new NanacocoaException(NOT_FOUND_PAYMENT));
    }

    private PaymentOperationResponse map(OperationRow row) {
        boolean pending = row.getPending() == 1;
        String hold = !pending ? "이미 처리된 거래입니다." : provider.isDisabled() ? "결제 연동이 비활성 상태입니다."
                : row.getNextRetryAt() == null ? "복구 예정 시각이 없어 운영 점검이 필요합니다."
                : row.getNextRetryAt().isAfter(LocalDateTime.now()) ? "복구 예정 시각 이후 다시 확인해주세요." : null;
        // 저장된 PG 오류 원문과 알 수 없는 코드가 그대로 응답에 포함되지 않게 한다.
        String category = switch (row.getFailureCode() == null ? "" : row.getFailureCode()) {
            case "" -> null;
            case "PG_PAYMENT_MISMATCH" -> "MISMATCH";
            case "ABORTED", "EXPIRED" -> "DECLINED";
            default -> "RECHECK_REQUIRED";
        };
        String message = category == null ? null : switch (category) {
            case "MISMATCH" -> "결제사 조회 결과가 일치하지 않아 확인이 필요합니다.";
            case "DECLINED" -> "결제사가 승인 실패를 확인했습니다.";
            default -> "결제 처리 결과를 다시 확인해야 합니다.";
        };
        return new PaymentOperationResponse(row.getType(), row.getId(), row.getPaymentId(), row.getOrderId(),
                row.getKind(), row.getAmount(), row.getStatus(), row.getPaymentStatus(), row.getOrderStatus(),
                row.getStockState(), row.getCreatedAt(), row.getUpdatedAt(), row.getRetryCount(), row.getNextRetryAt(),
                category, message, pending, pending && hold == null, hold);
    }
}
