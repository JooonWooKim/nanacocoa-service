package com.nanacocoa.server.policy.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public class PolicyReadiness {
    private volatile boolean available;
    @Value("${policy.signup-enabled:${POLICY_SIGNUP_ENABLED:false}}") private boolean signupEnabled;
    public boolean available() { return available && signupEnabled; }
    public void complete(boolean available) { this.available = available; }
}
