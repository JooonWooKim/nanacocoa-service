package com.nanacocoa.server.policy.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.policy.dto.response.PolicyResponse;
import com.nanacocoa.server.policy.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/policies") @RequiredArgsConstructor
public class PolicyController {
    private final PolicyService service;
    @GetMapping
    public ResponseEntity<SuccessMessage<PolicyResponse.Catalog>> catalog() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SuccessMessage<>("서비스 정책 조회 성공",service.catalog()));
    }
    @GetMapping("/{documentCode}/{version}")
    public ResponseEntity<SuccessMessage<PolicyResponse>> document(@PathVariable String documentCode, @PathVariable String version) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SuccessMessage<>("정책 문서 조회 성공",service.document(documentCode,version)));
    }
}
