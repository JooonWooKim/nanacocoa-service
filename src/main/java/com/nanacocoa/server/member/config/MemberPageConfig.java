package com.nanacocoa.server.member.config;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class MemberPageConfig {
    private final MemberService memberService;

    @Bean
    public WebMvcConfigurer memberPageWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                            throws Exception {
                        response.setHeader("Cache-Control", "no-store");
                        if (request.getUserPrincipal() != null) {
                            try {
                                memberService.requireProfileVerification(request.getUserPrincipal().getName());
                                return true;
                            } catch (NanacocoaException exception) {
                                // 미확인·만료된 세션에는 편집 화면 자체를 전달하지 않습니다.
                            }
                        }
                        response.sendRedirect(request.getContextPath() + "/mypage-verify.html");
                        return false;
                    }
                }).addPathPatterns("/mypage.html");
            }
        };
    }
}
