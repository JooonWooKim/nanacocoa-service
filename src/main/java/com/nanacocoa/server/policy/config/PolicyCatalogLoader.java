package com.nanacocoa.server.policy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.policy.service.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component @RequiredArgsConstructor
public class PolicyCatalogLoader implements ApplicationRunner {
    private final PolicyPublishingService publishing;
    private final PolicyReadiness readiness;
    private final ObjectMapper json;
    private final ResourceLoader resources;
    @Value("${policy.catalog-location:classpath:policies/catalog.json}") private String location;

    @Override public void run(ApplicationArguments args) {
        publishing.initialize();
        if (!location.startsWith("classpath:policies/") || location.contains("..")) {
            unavailable();
            return;
        }
        try (var input = resources.getResource(location).getInputStream()) {
            publishing.publish(json.readValue(input, PolicyCatalog.class));
            readiness.complete(true);
        } catch (IOException | NanacocoaException e) {
            // 원문·승인 정보·파일 경로를 로그에 남기지 않는다. 조회와 기존 회원 로그인은 유지한다.
            unavailable();
        }
    }
    private void unavailable() {
        readiness.complete(false);
        LoggerFactory.getLogger(getClass()).warn("POLICY_01: policy publication unavailable; new signup deferred");
    }
}
