package com.nanacocoa.server.member.controller;

import com.nanacocoa.server.member.service.ShippingAddressService;
import com.nanacocoa.server.member.dto.reqeust.ShippingAddressRequests.*;
import com.nanacocoa.server.member.dto.response.ShippingAddressResponse;
import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/members/me/shipping-addresses") @RequiredArgsConstructor
public class ShippingAddressController {
    private final ShippingAddressService service;
    @GetMapping
    public ResponseEntity<SuccessMessage<ShippingAddressResponse>> list(@AuthenticationPrincipal UserDetailsImpl user) {
        return result("배송지조회성공", service.list(user));
    }
    @PostMapping
    public ResponseEntity<SuccessMessage<ShippingAddressResponse>> create(@Valid @RequestBody Create request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "X-Checkout-Account", required = false) String account,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return result("배송지등록성공", service.create(request, key, account, user));
    }
    @PutMapping("/{id}")
    public ResponseEntity<SuccessMessage<ShippingAddressResponse>> update(@PathVariable Long id, @Valid @RequestBody Update request,
            @RequestHeader(value = "X-Checkout-Account", required = false) String account,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return result("배송지수정성공", service.update(id, request, account, user));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessMessage<ShippingAddressResponse>> delete(@PathVariable Long id,
            @RequestParam Long expectedVersion, @RequestParam Long expectedRevision,
            @RequestHeader(value = "X-Checkout-Account", required = false) String account,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return result("배송지삭제성공", service.delete(id, expectedVersion, expectedRevision, account, user));
    }
    @PutMapping("/{id}/default")
    public ResponseEntity<SuccessMessage<ShippingAddressResponse>> designate(@PathVariable Long id, @Valid @RequestBody Version request,
            @RequestHeader(value = "X-Checkout-Account", required = false) String account,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return result("기본배송지설정성공", service.designate(id, request, account, user));
    }
    private ResponseEntity<SuccessMessage<ShippingAddressResponse>> result(String message, ShippingAddressResponse data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SuccessMessage<>(message, data));
    }
}
