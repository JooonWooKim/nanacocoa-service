package com.nanacocoa.server.payment.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.payment.dto.response.PaymentOperationResponse;
import com.nanacocoa.server.payment.service.PaymentOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payment-operations")
@RequiredArgsConstructor
public class PaymentOperationsController {
    private final PaymentOperationsService service;

    @GetMapping
    public ResponseEntity<SuccessMessage<PaymentOperationResponse.Page>> list(
            @RequestParam(required = false) String kind, @RequestParam(required = false) String orderId,
            @RequestParam(defaultValue = "0") String page, @RequestParam(defaultValue = "20") String size,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SuccessMessage<>("미확정 거래 조회 성공", service.list(kind, orderId, page, size, user)));
    }

    @GetMapping("/{type}/{id}")
    public ResponseEntity<SuccessMessage<PaymentOperationResponse>> get(@PathVariable String type,
            @PathVariable String id, @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SuccessMessage<>("거래 상세 조회 성공", service.get(type, id, user)));
    }

    @PostMapping("/{type}/{id}/reconcile")
    public ResponseEntity<SuccessMessage<PaymentOperationResponse>> reconcile(@PathVariable String type,
            @PathVariable String id, @AuthenticationPrincipal UserDetailsImpl user) {
        var detail = service.reconcile(type, id, user);
        return ResponseEntity.status(detail.pending() ? 202 : 200).cacheControl(CacheControl.noStore())
                .body(new SuccessMessage<>(detail.pending() ? "거래 결과 확인 중" : "거래 복구 확인 완료", detail));
    }
}
