package com.nanacocoa.server.support.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.repository.OrderRepository;
import com.nanacocoa.server.support.dto.reqeust.SupportRequests;
import com.nanacocoa.server.support.dto.response.SupportResponses.*;
import com.nanacocoa.server.support.entity.Inquiry;
import com.nanacocoa.server.support.repository.InquiryRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class SupportService {
    private final FaqCatalog faq;
    private final MemberRepository members;
    private final OrderRepository orders;
    private final InquiryRepository inquiries;

    public List<Faq> faq() { return faq.entries(); }

    @Transactional
    public Receipt create(SupportRequests.Create input, String suppliedKey, String account, UserDetailsImpl user) {
        Long memberId = memberId(user);
        // Serialize inquiry submission for this member, including replays after an ambiguous response.
        var owner = members.findByIdForUpdate(memberId).orElseThrow(() -> new NanacocoaException(AUTHENTICATION_REQUIRED));
        verifyAccount(account, owner);
        String title = required(input.title(), 100);
        String body = required(input.body(), 2000);
        String key = key(suppliedKey);
        var previous = inquiries.findByMemberIdAndRequestKey(memberId, key);
        String number = input.orderId() == null ? null : required(input.orderId(), 64);
        if (previous.isPresent()) {
            Detail saved = detail(previous.get());
            if (!title.equals(saved.title()) || !body.equals(saved.body()) || !Objects.equals(number, saved.orderId())) {
                throw new NanacocoaException(SUPPORT_REQUEST_CONFLICT);
            }
            return new Receipt(saved, true);
        }
        Long orderId = number == null ? null : orders.findByOrderIdAndMemberId(number, memberId)
                .filter(order -> number.equals(order.getOrderId()))
                .orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER)).getId();
        var saved = inquiries.save(new Inquiry(memberId, orderId, key, title, body, now()));
        return new Receipt(Detail.from(saved, number), false);
    }

    @Transactional(readOnly = true)
    public Page own(String page, UserDetailsImpl user) {
        return list(inquiries.findByMemberId(memberId(user), pagination(page)));
    }

    @Transactional(readOnly = true)
    public Detail ownDetail(String id, UserDetailsImpl user) {
        return detail(inquiries.findByIdAndMemberId(inquiryId(id), memberId(user))
                .orElseThrow(() -> new NanacocoaException(SUPPORT_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public Page adminList(String status, String page, UserDetailsImpl user) {
        requireAdmin(user);
        Pageable pageable = pagination(page);
        if (status == null) return list(inquiries.findAllInquiries(pageable));
        Inquiry.Status filter;
        try { filter = Inquiry.Status.valueOf(status); }
        catch (IllegalArgumentException invalid) { throw new NanacocoaException(INVALID_REQUEST); }
        return list(inquiries.findByStatus(filter, pageable));
    }

    @Transactional(readOnly = true)
    public Detail adminDetail(String id, UserDetailsImpl user) {
        requireAdmin(user);
        return detail(inquiries.findById(inquiryId(id)).orElseThrow(() -> new NanacocoaException(SUPPORT_NOT_FOUND)));
    }

    @Transactional
    public Detail reply(String id, SupportRequests.Reply input, String account, UserDetailsImpl user) {
        var operator = adminMember(user);
        verifyAccount(account, operator);
        String body = required(input.body(), 2000);
        var inquiry = inquiries.findForUpdate(inquiryId(id))
                .orElseThrow(() -> new NanacocoaException(SUPPORT_NOT_FOUND));
        if (inquiry.getStatus() == Inquiry.Status.ANSWERED) throw new NanacocoaException(SUPPORT_REPLY_CONFLICT);
        inquiry.answer(body, operator.getId(), now());
        return detail(inquiry);
    }

    private static Page list(Slice<Inquiry> page) {
        return new Page(page.stream().map(Summary::from).toList(), page.getNumber(), page.hasNext());
    }

    private Detail detail(Inquiry inquiry) {
        Long record = inquiry.getOrderRecordId();
        return Detail.from(inquiry, record == null ? null : orders.findById(record).map(Order::getOrderId).orElse(null));
    }

    private static Long memberId(UserDetailsImpl user) {
        if (user == null || user.getMemberId() == null) throw new NanacocoaException(AUTHENTICATION_REQUIRED);
        return user.getMemberId();
    }

    private void requireAdmin(UserDetailsImpl user) { adminMember(user); }

    private Member adminMember(UserDetailsImpl user) {
        var member = members.findById(memberId(user)).orElseThrow(() -> new NanacocoaException(AUTHENTICATION_REQUIRED));
        member.checkAdmin();
        return member;
    }

    private static void verifyAccount(String account, Member member) {
        if (account != null && !account.equals(member.checkoutAccount())) throw new NanacocoaException(SUPPORT_ACCOUNT_CHANGED);
    }

    private static Pageable pagination(String page) {
        try {
            int number = Integer.parseInt(page);
            if (number < 0 || number > Integer.MAX_VALUE / 20) throw new IllegalArgumentException();
            return PageRequest.of(number, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        } catch (IllegalArgumentException invalid) { throw new NanacocoaException(INVALID_REQUEST); }
    }

    private static Long inquiryId(String id) {
        try {
            long value = Long.parseLong(id);
            if (value > 0) return value;
        } catch (NumberFormatException ignored) {}
        throw new NanacocoaException(SUPPORT_NOT_FOUND);
    }

    private static String required(String value, int max) {
        String clean = value == null ? "" : value.strip();
        if (clean.isEmpty() || clean.length() > max) throw new NanacocoaException(INVALID_REQUEST);
        return clean;
    }

    private static String key(String value) {
        try {
            UUID id = UUID.fromString(value == null ? "" : value);
            if (id.toString().equalsIgnoreCase(value)) return id.toString();
        } catch (IllegalArgumentException ignored) {}
        throw new NanacocoaException(INVALID_REQUEST);
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
