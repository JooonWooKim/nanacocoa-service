package com.nanacocoa.server.notification.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.notification.service.RestockSubscriptionService;
import com.nanacocoa.server.notification.dto.response.RestockSubscriptionState;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/restock-subscriptions/{productId}")
@RequiredArgsConstructor
public class RestockSubscriptionController {
    private final RestockSubscriptionService subscriptions;

    @GetMapping
    public ResponseEntity<SuccessMessage<RestockSubscriptionState>> state(@PathVariable Long productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return response(subscriptions.state(productId, user));
    }

    @PutMapping
    public ResponseEntity<SuccessMessage<RestockSubscriptionState>> subscribe(@PathVariable Long productId,
            @RequestHeader(value = "X-Checkout-Account", required = false) String account,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return response(subscriptions.subscribe(productId, account, user));
    }

    @DeleteMapping
    public ResponseEntity<SuccessMessage<RestockSubscriptionState>> cancel(@PathVariable Long productId,
            @RequestHeader(value = "X-Checkout-Account", required = false) String account,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return response(subscriptions.cancel(productId, account, user));
    }

    private static ResponseEntity<SuccessMessage<RestockSubscriptionState>> response(RestockSubscriptionState state) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SuccessMessage<>("재입고 알림 신청 상태", state));
    }
}
