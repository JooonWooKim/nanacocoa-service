package com.nanacocoa.server.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.common.exception.ErrorCode;
import com.nanacocoa.server.common.exception.ErrorMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class SessionGenerationFilter extends OncePerRequestFilter {
    private final SessionGenerationVerifier verifier;
    private final ObjectMapper json;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl user) {
            boolean current;
            try {
                current = verifier.isCurrent(user.getMemberId(), user.getAuthenticationGeneration());
            } catch (RuntimeException unavailable) {
                var error = ErrorCode.SESSION_CHECK_UNAVAILABLE;
                response.setStatus(error.getHttpStatus().value());
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Cache-Control", "no-store");
                json.writeValue(response.getOutputStream(), new ErrorMessage(error.getCode(), error.getError()));
                return;
            }
            if (!current) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
            }
        }
        // 복구 API와 화면에는 오류 응답을 포함해 캐시를 남기지 않는다.
        if (request.getRequestURI().startsWith("/api/auth/password-recovery")
                || request.getRequestURI().equals("/password-reset.html")) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Referrer-Policy", "no-referrer");
        }
        chain.doFilter(request, response);
    }
}
