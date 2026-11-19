package com.nanacocoa.server.order.controller;
import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.order.dto.reqeust.CreateReturnRequest;
import com.nanacocoa.server.order.dto.response.ReturnResponse;
import com.nanacocoa.server.order.service.ReturnRequestFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders/{orderId}/return-requests")
@RequiredArgsConstructor
public class ReturnRequestController {
    private final ReturnRequestFacade returns;
    @GetMapping
    public ResponseEntity<SuccessMessage<ReturnResponse.OrderView>> get(@PathVariable String orderId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new SuccessMessage<>("취소·반품 신청 조회 성공", returns.ownedView(orderId, user)));
    }
    @PostMapping
    public ResponseEntity<SuccessMessage<ReturnResponse.OrderView>> create(@PathVariable String orderId,
            @Valid @RequestBody CreateReturnRequest request, @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new SuccessMessage<>("취소·반품 신청 접수", returns.create(orderId, request, user)));
    }
}
