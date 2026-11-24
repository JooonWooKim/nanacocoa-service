package com.nanacocoa.server.notification.service;

import com.nanacocoa.server.notification.entity.OrderNotification;
import com.nanacocoa.server.notification.entity.OrderNotification.Status;
import com.nanacocoa.server.notification.entity.RestockSubscription;
import com.nanacocoa.server.notification.repository.OrderNotificationRepository;
import com.nanacocoa.server.notification.repository.RestockSubscriptionRepository;
import com.nanacocoa.server.notification.repository.RestockSubscriptionRepository.Candidate;
import com.nanacocoa.server.products.config.RestockNotificationSettings;
import com.nanacocoa.server.products.entity.ProductRestockEvent;
import com.nanacocoa.server.products.repository.ProductRestockEventRepository;
import com.nanacocoa.server.products.repository.ProductsRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestockNotificationPublisher {
    private final ProductsRepository products;
    private final ProductRestockEventRepository events;
    private final RestockSubscriptionRepository subscriptions;
    private final OrderNotificationRepository notifications;
    private final RestockNotificationSettings settings;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> prepare(Candidate candidate) {
        return eligible(candidate).flatMap(eligible -> {
            var notification = notifications.lockRestock(candidate.getSubscriptionId())
                    .orElseGet(() -> notifications.save(OrderNotification.productRestock(
                            eligible.subscription(), eligible.event().getConfirmedAt())));
            return notification.getStatus() == Status.PENDING ? Optional.of(notification.getId()) : Optional.empty();
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Candidate candidate, Long notificationId) {
        eligible(candidate).ifPresent(eligible -> notifications.lockRestock(candidate.getSubscriptionId())
                .filter(row -> row.getId().equals(notificationId) && row.getStatus() == Status.PENDING)
                .ifPresent(row -> {
                    row.publishRestock(eligible.event().getConfirmedAt());
                    eligible.subscription().close();
                    notifications.flush();
                }));
    }

    private Optional<Eligible> eligible(Candidate candidate) {
        var product = products.findByIdForUpdate(candidate.getProductId()).orElse(null);
        if (product == null || !product.isPurchasable()) return Optional.empty();
        var subscription = subscriptions.lockById(candidate.getSubscriptionId()).orElse(null);
        if (subscription == null || !subscription.isActive() || !subscription.getProductId().equals(product.getId())
                || subscription.getSubscribedEpoch() >= product.getAvailabilityEpoch()) return Optional.empty();
        return events.findByProductIdAndAvailabilityEpoch(product.getId(), product.getAvailabilityEpoch())
                .filter(event -> !event.getConfirmedAt().isBefore(settings.cutoff()))
                .map(event -> new Eligible(subscription, event));
    }

    private record Eligible(RestockSubscription subscription, ProductRestockEvent event) {}
}
