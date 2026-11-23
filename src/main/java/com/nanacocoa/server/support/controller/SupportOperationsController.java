package com.nanacocoa.server.support.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.support.dto.reqeust.SupportRequests;
import com.nanacocoa.server.support.dto.response.SupportResponses.*;
import com.nanacocoa.server.support.service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/support/inquiries")
@RequiredArgsConstructor
public class SupportOperationsController {
    private final SupportService support;

    @GetMapping
    public ResponseEntity<SuccessMessage<Page>> list(@RequestParam(defaultValue = "0") String page,
            @RequestParam(required = false) String status, @AuthenticationPrincipal UserDetailsImpl user) {
        return response("운영 문의 목록 조회 성공", support.adminList(status, page, user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessMessage<Detail>> detail(@PathVariable String id,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return response("운영 문의 상세 조회 성공", support.adminDetail(id, user));
    }

    @PostMapping("/{id}/reply")
    public ResponseEntity<SuccessMessage<Detail>> reply(@PathVariable String id,
            @RequestBody SupportRequests.Reply input,
            @RequestHeader(value = "X-Checkout-Account", required = false) String account,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return response("문의 답변 저장 성공", support.reply(id, input, account, user));
    }

    private static <T> ResponseEntity<SuccessMessage<T>> response(String message, T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SuccessMessage<>(message, data));
    }
}
