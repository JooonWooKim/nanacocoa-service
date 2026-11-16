package com.nanacocoa.server.common.session;

import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.common.userdetails.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@AllArgsConstructor
public class SessionLoginService implements LoginService{
	private static final String USER_ID = "user_id";
	private final HttpServletRequest request;
	private final HttpSessionCsrfTokenRepository csrfTokens;
	private final ProfileVerificationSession verificationSession;
	private final UserDetailsServiceImpl userDetailsService;

	@Override
	public void login(String email) {
		UserDetailsImpl userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(email);
		var session = request.getSession();
		request.changeSessionId();
		csrfTokens.saveToken(null, request, null);
		verificationSession.clear();
		session.setAttribute(USER_ID, email);
		Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails,
				null,
				Collections.emptyList());

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);

		SecurityContextHolder.setContext(context);
		session.setAttribute("SPRING_SECURITY_CONTEXT", context);
	}

	@Override
	public void logout() {
		SecurityContextHolder.clearContext();
		var session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}
}
