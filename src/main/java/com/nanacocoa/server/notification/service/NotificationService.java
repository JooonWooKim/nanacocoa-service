package com.nanacocoa.server.notification.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.notification.dto.response.NotificationResponses.*;
import com.nanacocoa.server.notification.entity.OrderNotification.Status;
import com.nanacocoa.server.notification.repository.OrderNotificationRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final OrderNotificationRepository notifications;
    private final MemberRepository members;

    @Transactional(readOnly = true)
    public Page list(String page, UserDetailsImpl user) {
        Long member = memberId(user);
        var rows = notifications.findByMemberIdAndStatus(member, Status.PUBLISHED, pageable(page));
        return new Page(rows.stream().map(Item::from).toList(), rows.getNumber(), rows.hasNext(),
                notifications.countByMemberIdAndStatusAndReadAtIsNull(member, Status.PUBLISHED));
    }

    @Transactional
    public Item read(String id, UserDetailsImpl user) {
        var item = notifications.findByIdAndMemberIdAndStatus(parseId(id), memberId(user), Status.PUBLISHED)
                .orElseThrow(() -> new NanacocoaException(NOTIFICATION_NOT_FOUND));
        item.read(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        return Item.from(item);
    }

    @Transactional(readOnly = true)
    public OperationsPage operations(String page, String filter, UserDetailsImpl user) {
        admin(user);
        Status status;
        try { status = Status.valueOf(filter); }
        catch (IllegalArgumentException invalid) { throw new NanacocoaException(INVALID_REQUEST); }
        var rows = notifications.findByStatus(status, pageable(page));
        return new OperationsPage(rows.stream().map(Operation::from).toList(), rows.getNumber(), rows.hasNext());
    }

    @Transactional
    public Operation retry(String id, UserDetailsImpl user) {
        admin(user);
        var row = notifications.lockById(parseId(id))
                .orElseThrow(() -> new NanacocoaException(NOTIFICATION_NOT_FOUND));
        row.retry();
        return Operation.from(row);
    }

    private void admin(UserDetailsImpl user) {
        members.findById(memberId(user)).orElseThrow(() -> new NanacocoaException(AUTHENTICATION_REQUIRED)).checkAdmin();
    }

    private static Long memberId(UserDetailsImpl user) {
        if (user == null || user.getMemberId() == null) throw new NanacocoaException(AUTHENTICATION_REQUIRED);
        return user.getMemberId();
    }

    private static Long parseId(String id) {
        try {
            long value = Long.parseLong(id);
            if (value > 0) return value;
        } catch (NumberFormatException ignored) {}
        throw new NanacocoaException(NOTIFICATION_NOT_FOUND);
    }

    private static PageRequest pageable(String page) {
        try {
            int number = Integer.parseInt(page);
            if (number < 0 || number > Integer.MAX_VALUE / 20) throw new IllegalArgumentException();
            return PageRequest.of(number, 20, Sort.by(Sort.Direction.DESC, "confirmedAt", "id"));
        } catch (IllegalArgumentException invalid) { throw new NanacocoaException(INVALID_REQUEST); }
    }
}
