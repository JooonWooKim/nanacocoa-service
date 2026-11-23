package com.nanacocoa.server.support.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.support.dto.reqeust.SupportRequests;
import com.nanacocoa.server.support.dto.response.SupportResponses.*;
import com.nanacocoa.server.support.service.SupportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {
    private final SupportService support;

    @GetMapping("/faq")
    public ResponseEntity<SuccessMessage<List<Faq>>> faq() {
        return ResponseEntity.ok().body(new SuccessMessage<>("FAQ 조회 성공", support.faq()));
    }

    @PostMapping("/inquiries")
    public ResponseEntity<SuccessMessage<Detail>> create(@RequestBody SupportRequests.Create input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "X-Checkout-Account", required = false) String account,
            @AuthenticationPrincipal UserDetailsImpl user) {
        Receipt result = support.create(input, key, account, user);
        return ResponseEntity.status(result.replayed() ? 200 : 201).cacheControl(CacheControl.noStore())
                .body(new SuccessMessage<>("문의 접수 성공", result.inquiry()));
    }

    @GetMapping("/inquiries")
    public ResponseEntity<SuccessMessage<Page>> list(@RequestParam(defaultValue = "0") String page,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SuccessMessage<>("문의 목록 조회 성공", support.own(page, user)));
    }

    @GetMapping("/inquiries/{id}")
    public ResponseEntity<SuccessMessage<Detail>> detail(@PathVariable String id,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SuccessMessage<>("문의 조회 성공", support.ownDetail(id, user)));
    }
}
