package com.nanacocoa.server.order.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.order.dto.reqeust.ShippingChangeRequest;
import com.nanacocoa.server.order.dto.response.ShippingResponse;
import com.nanacocoa.server.order.service.ShippingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/shipping/orders")
@RequiredArgsConstructor
public class ShippingController {
    private final ShippingService shippingService;

    @GetMapping
    public ResponseEntity<SuccessMessage<ShippingResponse.Page>> list(
            @RequestParam(required = false) String orderNumber, @RequestParam(defaultValue = "PREPARING") String state,
            @RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SuccessMessage<>("배송 목록 조회 성공", shippingService.list(orderNumber, state, page, user)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<SuccessMessage<ShippingResponse.Detail>> get(
            @PathVariable String orderId, @AuthenticationPrincipal UserDetailsImpl user) {
        ShippingResponse.Detail detail = shippingService.get(orderId, user);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SuccessMessage<>("배송 상세 조회 성공", detail));
    }

    @PostMapping("/{orderId}")
    public ResponseEntity<SuccessMessage<ShippingResponse.Detail>> change(
            @PathVariable String orderId, @Valid @RequestBody ShippingChangeRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SuccessMessage<>("배송 정보 변경 성공", shippingService.change(orderId, request, user)));
    }
}
