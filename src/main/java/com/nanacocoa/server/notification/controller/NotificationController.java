package com.nanacocoa.server.notification.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.notification.dto.response.NotificationResponses.*;
import com.nanacocoa.server.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notifications;

    @GetMapping("/api/notifications")
    public ResponseEntity<SuccessMessage<Page>> list(@RequestParam(defaultValue = "0") String page,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return response("알림 조회 성공", notifications.list(page, user));
    }

    @PutMapping("/api/notifications/{id}/read")
    public ResponseEntity<SuccessMessage<Item>> read(@PathVariable String id,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return response("알림 읽음 처리 성공", notifications.read(id, user));
    }

    @GetMapping("/api/admin/notifications")
    public ResponseEntity<SuccessMessage<OperationsPage>> operations(@RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "FAILED") String status, @AuthenticationPrincipal UserDetailsImpl user) {
        return response("알림 처리 목록 조회 성공", notifications.operations(page, status, user));
    }

    @PostMapping("/api/admin/notifications/{id}/retry")
    public ResponseEntity<SuccessMessage<Operation>> retry(@PathVariable String id,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return response("알림 재시도 요청 성공", notifications.retry(id, user));
    }

    private static <T> ResponseEntity<SuccessMessage<T>> response(String message, T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SuccessMessage<>(message, data));
    }
}
