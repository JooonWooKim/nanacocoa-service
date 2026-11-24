package com.nanacocoa.server.notification.service;

import com.nanacocoa.server.notification.repository.RestockSubscriptionRepository;
import com.nanacocoa.server.notification.repository.RestockSubscriptionRepository.Candidate;
import com.nanacocoa.server.products.config.RestockNotificationSettings;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"notifications.enabled", "notifications.restock.enabled"}, havingValue = "true")
public class RestockNotificationWorker {
    private static final Logger log = LoggerFactory.getLogger(RestockNotificationWorker.class);
    private final RestockSubscriptionRepository subscriptions;
    private final RestockNotificationPublisher publisher;
    private final NotificationPublisher failures;
    private final RestockNotificationSettings settings;

    @Scheduled(fixedDelayString = "${notifications.fixed-delay:30000}", initialDelayString = "${notifications.initial-delay:30000}")
    public void run() {
        try {
            for (var candidate : subscriptions.ready(settings.cutoff())) {
                try {
                    publisher.prepare(candidate).ifPresent(id -> publish(candidate, id));
                } catch (RuntimeException deferred) {
                    log.warn("Restock notification preparation deferred");
                }
            }
        } catch (RuntimeException deferred) {
            log.warn("Restock notification discovery deferred");
        }
    }

    private void publish(Candidate candidate, Long id) {
        try {
            publisher.publish(candidate, id);
        } catch (RuntimeException failed) {
            try {
                failures.recordFailure(id);
            } catch (RuntimeException deferred) {
                log.warn("Restock notification failure record deferred; notification id={}", id);
            }
        }
    }
}
