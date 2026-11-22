package com.nanacocoa.server.recovery.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.recovery.dto.reqeust.RecoveryRequests;
import com.nanacocoa.server.recovery.service.RecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/auth/password-recovery")
public class RecoveryController {
    private final RecoveryService service;
    @PostMapping(value = "/requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessMessage<Void>> request(@Valid @RequestBody RecoveryRequests.Request body, HttpServletRequest request) {
        service.request(body, request.getRemoteAddr());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new SuccessMessage<>(RecoveryService.ACCEPTED, null));
    }
    @PostMapping(value = "/reset", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessMessage<Void>> reset(@Valid @RequestBody RecoveryRequests.Reset body) {
        service.reset(body);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SuccessMessage<>("비밀번호가 변경되었습니다. 다시 로그인해주세요.", null));
    }
}
