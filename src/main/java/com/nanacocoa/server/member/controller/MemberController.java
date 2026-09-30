package com.nanacocoa.server.member.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.dto.reqeust.ChangeMemberPasswordRequest;
import com.nanacocoa.server.member.dto.reqeust.UpdateMemberProfileRequest;
import com.nanacocoa.server.member.dto.response.MemberProfileResponse;
import com.nanacocoa.server.member.service.MemberService;
import com.nanacocoa.server.member.dto.reqeust.VerifyMemberPasswordRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @PostMapping(value = "/verification", consumes = "application/json")
    public ResponseEntity<SuccessMessage<Void>> verifyPassword(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody VerifyMemberPasswordRequest request,
            @RequestHeader(value = "X-Mypage-Verification", defaultValue = "") String intent
    ) {
        memberService.verifyPassword(userDetails.getUsername(), request, intent);
        return ResponseEntity.ok(new SuccessMessage<>("본인확인성공", null));
    }

    @GetMapping
    public ResponseEntity<SuccessMessage<MemberProfileResponse>> getProfile(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        MemberProfileResponse response = memberService.getProfile(userDetails.getUsername());
        return ResponseEntity.ok(new SuccessMessage<>("회원정보조회성공", response));
    }

    @PutMapping
    public ResponseEntity<SuccessMessage<MemberProfileResponse>> updateProfile(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody UpdateMemberProfileRequest request
    ) {
        MemberProfileResponse response = memberService.updateProfile(userDetails.getUsername(), request);
        return ResponseEntity.ok(new SuccessMessage<>("회원정보수정성공", response));
    }

    @PutMapping("/password")
    public ResponseEntity<SuccessMessage<Void>> changePassword(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody ChangeMemberPasswordRequest request
    ) {
        memberService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok(new SuccessMessage<>("비밀번호변경성공", null));
    }
}
