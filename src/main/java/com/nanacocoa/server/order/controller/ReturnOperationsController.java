package com.nanacocoa.server.order.controller;
import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.order.dto.reqeust.ReturnActionRequest;
import com.nanacocoa.server.order.dto.response.ReturnResponse;
import com.nanacocoa.server.order.service.ReturnRequestFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/return-requests")
@RequiredArgsConstructor
public class ReturnOperationsController {
    private final ReturnRequestFacade returns;
    @GetMapping
    public ResponseEntity<SuccessMessage<ReturnResponse.Page>> list(
            @RequestParam(required = false) String orderId, @RequestParam(required = false) String type,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") String page,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new SuccessMessage<>("취소·반품 운영 목록 조회 성공", returns.list(orderId, type, status, page, user)));
    }
    @GetMapping("/{id}")
    public ResponseEntity<SuccessMessage<ReturnResponse.Detail>> get(@PathVariable String id,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new SuccessMessage<>("취소·반품 운영 상세 조회 성공", returns.get(id, user)));
    }
    @PostMapping("/{id}/actions")
    public ResponseEntity<SuccessMessage<ReturnResponse.Detail>> change(@PathVariable String id,
            @Valid @RequestBody ReturnActionRequest request, @AuthenticationPrincipal UserDetailsImpl user) {
        var result = returns.action(id, request, user);
        return ResponseEntity.status(result.pending() ? 202 : 200).cacheControl(CacheControl.noStore())
            .body(new SuccessMessage<>("취소·반품 처리 결과", result));
    }
}
