package com.nanacocoa.server.products.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RestockNotificationSettings {
    private final LocalDateTime cutoff;

    public RestockNotificationSettings(
            @Value("${notifications.enabled:false}") boolean notificationsEnabled,
            @Value("${notifications.restock.enabled:false}") boolean enabled,
            @Value("${notifications.restock.starts-at:}") String startsAt) {
        cutoff = notificationsEnabled && enabled
                ? LocalDateTime.ofInstant(Instant.parse(startsAt), ZoneOffset.UTC) : null;
    }

    public boolean enabled() { return cutoff != null; }
    public LocalDateTime cutoff() { return cutoff; }
}
