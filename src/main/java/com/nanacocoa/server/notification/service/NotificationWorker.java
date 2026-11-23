package com.nanacocoa.server.notification.service;

import com.nanacocoa.server.notification.entity.OrderNotification.Status;
import com.nanacocoa.server.notification.entity.OrderNotification.Type;
import com.nanacocoa.server.notification.repository.OrderNotificationRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notifications.enabled", havingValue = "true")
public class NotificationWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
    private final OrderNotificationRepository notifications;
    private final NotificationPublisher publisher;
    private final LocalDateTime cutoff;
    private final LocalDateTime supportCutoff;

    public NotificationWorker(OrderNotificationRepository notifications, NotificationPublisher publisher,
            @Value("${notifications.starts-at:}") String startsAt,
            @Value("${notifications.support.enabled:false}") boolean supportEnabled,
            @Value("${notifications.support.starts-at:}") String supportStartsAt) {
        this.notifications = notifications;
        this.publisher = publisher;
        this.cutoff = LocalDateTime.ofInstant(Instant.parse(startsAt), ZoneOffset.UTC);
        this.supportCutoff = supportEnabled ? LocalDateTime.ofInstant(Instant.parse(supportStartsAt), ZoneOffset.UTC) : null;
    }

    @Scheduled(fixedDelayString = "${notifications.fixed-delay:30000}", initialDelayString = "${notifications.initial-delay:30000}")
    public void run() {
        runOrders();
        if (supportCutoff != null) runReplies();
    }

    private void runOrders() {
        try {
            for (var event : notifications.undiscovered(cutoff)) {
                try {
                    publisher.discover(event);
                } catch (DataIntegrityViolationException failure) {
                    if (!notifications.existsByOrderIdAndEventType(event.getOrderId(), Type.valueOf(event.getEventType()))) {
                        log.warn("Order notification discovery deferred");
                    }
                } catch (RuntimeException failure) {
                    log.warn("Order notification discovery deferred");
                }
            }
            publishPending(cutoff, false);
        } catch (RuntimeException unavailable) {
            log.warn("Order notification scan deferred");
        }
    }

    private void runReplies() {
        try {
            for (var event : notifications.undiscoveredReplies(supportCutoff)) {
                try {
                    publisher.discoverReply(event);
                } catch (DataIntegrityViolationException failure) {
                    if (!notifications.existsByInquiryId(event.getInquiryId())) {
                        log.warn("Support notification discovery deferred");
                    }
                } catch (RuntimeException failure) {
                    log.warn("Support notification discovery deferred");
                }
            }
            publishPending(supportCutoff, true);
        } catch (RuntimeException unavailable) {
            log.warn("Support notification scan deferred");
        }
    }

    private void publishPending(LocalDateTime startsAt, boolean replies) {
        for (Long id : notifications.pendingIds(Status.PENDING, startsAt, replies, PageRequest.of(0, 100))) {
            try {
                publisher.publish(id);
            } catch (RuntimeException failure) {
                try {
                    publisher.recordFailure(id);
                } catch (RuntimeException unavailable) {
                    log.warn("Notification failure record deferred; notification id={}", id);
                }
            }
        }
    }
}
