package com.nanacocoa.server.common.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.common.exception.ErrorCode;
import com.nanacocoa.server.common.exception.ErrorMessage;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, HttpSessionCsrfTokenRepository csrfTokens,
                                            ObjectMapper json) throws Exception {
        var defaultDeniedHandler = new AccessDeniedHandlerImpl();
        return http
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
            .logout(logout -> logout.disable())
            .formLogin(formLogin -> formLogin.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((request, response, exception) -> {
                    if (!(exception instanceof CsrfException)) {
                        defaultDeniedHandler.handle(request, response, exception);
                        return;
                    }
                    var error = ErrorCode.CSRF_INVALID;
                    response.setStatus(error.getHttpStatus().value());
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.setHeader("Cache-Control", "no-store");
                    json.writeValue(response.getOutputStream(), new ErrorMessage(error.getCode(), error.getError()));
                }))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/*.html",
                    "/*.css",
                    "/*.js",
                    "/*.png",
                    "/*.jpg",
                    "/*.jpeg",
                    "/*.JPG",
                    "/*.mov",
                    "/*.MOV",
                    "/api/auth/**",
                    "/api/products/**",
                    "/api/health",
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/policies", "/api/policies/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                .anyRequest().authenticated()
            )
            .build();
    }
}
