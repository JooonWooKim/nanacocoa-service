package com.nanacocoa.server.member.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.dto.reqeust.LoginRequest;
import com.nanacocoa.server.member.dto.reqeust.SignupRequest;
import com.nanacocoa.server.member.dto.response.AuthSessionResponse;
import com.nanacocoa.server.member.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
	private final AuthService authService;

	@PostMapping("/signup")
	public ResponseEntity<SuccessMessage<Void>> signup(@Valid @RequestBody SignupRequest request) {
		authService.signUp(request);
		return new ResponseEntity<>(new SuccessMessage<>("회원가입성공", null), HttpStatus.CREATED);
	}

	@PostMapping("/login")
	public ResponseEntity<SuccessMessage<Void>> login(@RequestBody LoginRequest request) {
		authService.login(request);
		return new ResponseEntity<>(new SuccessMessage<>("로그인성공", null), HttpStatus.OK);
	}

	@PostMapping("/logout")
	public ResponseEntity<AuthSessionResponse> logout() {
		authService.logout();
		return ResponseEntity.ok(AuthSessionResponse.anonymous());
	}

	@GetMapping("/csrf")
	public ResponseEntity<SuccessMessage<CsrfResponse>> csrf(@RequestAttribute("_csrf") CsrfToken token) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(new SuccessMessage<>("보안 토큰 발급 성공", new CsrfResponse(token.getHeaderName(), token.getToken())));
	}

	public record CsrfResponse(String headerName, String token) {}

	@GetMapping("/session")
	public ResponseEntity<AuthSessionResponse> session(@AuthenticationPrincipal UserDetailsImpl userDetails) {
		return ResponseEntity.ok(authService.getSession(userDetails));
	}
}
