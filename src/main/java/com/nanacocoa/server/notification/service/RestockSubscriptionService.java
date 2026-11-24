package com.nanacocoa.server.notification.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.notification.dto.response.RestockSubscriptionState;
import com.nanacocoa.server.notification.entity.OrderNotification.Status;
import com.nanacocoa.server.notification.entity.RestockSubscription;
import com.nanacocoa.server.notification.repository.OrderNotificationRepository;
import com.nanacocoa.server.notification.repository.RestockSubscriptionRepository;
import com.nanacocoa.server.products.config.RestockNotificationSettings;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.products.repository.ProductsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class RestockSubscriptionService {
    private final ProductsRepository products;
    private final MemberRepository members;
    private final RestockSubscriptionRepository subscriptions;
    private final OrderNotificationRepository notifications;
    private final RestockNotificationSettings settings;

    @Transactional(readOnly = true)
    public RestockSubscriptionState state(Long productId, UserDetailsImpl user) {
        var member = member(user);
        var product = products.findById(productId).orElseThrow(() -> new NanacocoaException(NOT_FOUND_PRODUCTS));
        return state(product, subscriptions.findByProductIdAndMemberIdAndActiveTrue(productId, member.getId()).isPresent());
    }

    @Transactional
    public RestockSubscriptionState subscribe(Long productId, String account, UserDetailsImpl user) {
        var member = member(user);
        verifyAccount(member, account);
        var product = lockProduct(productId);
        if (subscriptions.lockActive(productId, member.getId()).isPresent()) return state(product, true);
        if (!settings.enabled()) throw new NanacocoaException(RESTOCK_UNAVAILABLE);
        if (!soldOut(product)) throw new NanacocoaException(RESTOCK_NOT_SOLD_OUT);
        subscriptions.save(new RestockSubscription(productId, member.getId(), product.getAvailabilityEpoch()));
        return state(product, true);
    }

    @Transactional
    public RestockSubscriptionState cancel(Long productId, String account, UserDetailsImpl user) {
        var member = member(user);
        verifyAccount(member, account);
        var product = lockProduct(productId);
        subscriptions.lockActive(productId, member.getId()).ifPresent(subscription -> {
            subscription.close();
            notifications.lockRestock(subscription.getId()).filter(row -> row.getStatus() != Status.PUBLISHED)
                    .ifPresent(notifications::delete);
        });
        return state(product, false);
    }

    private RestockSubscriptionState state(Products product, boolean active) {
        return new RestockSubscriptionState(active, !active && settings.enabled() && soldOut(product));
    }

    private Products lockProduct(Long id) {
        return products.findByIdForUpdate(id).orElseThrow(() -> new NanacocoaException(NOT_FOUND_PRODUCTS));
    }

    private static boolean soldOut(Products product) { return "SOLD_OUT".equals(product.getAvailability()); }

    private Member member(UserDetailsImpl user) {
        if (user == null || user.getMemberId() == null) throw new NanacocoaException(AUTHENTICATION_REQUIRED);
        return members.findById(user.getMemberId()).orElseThrow(() -> new NanacocoaException(AUTHENTICATION_REQUIRED));
    }

    private static void verifyAccount(Member member, String account) {
        if (account != null && !account.equals(member.checkoutAccount())) {
            throw new NanacocoaException(ORDER_CHECKOUT_ACCOUNT_CHANGED);
        }
    }
}
