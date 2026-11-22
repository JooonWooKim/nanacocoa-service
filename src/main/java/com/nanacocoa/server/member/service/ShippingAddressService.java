package com.nanacocoa.server.member.service;

import com.nanacocoa.server.member.entity.*;
import com.nanacocoa.server.member.repository.*;
import com.nanacocoa.server.member.dto.reqeust.ShippingAddressRequests.*;
import com.nanacocoa.server.member.dto.response.ShippingAddressResponse;
import com.nanacocoa.server.common.exception.*;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Service @RequiredArgsConstructor
public class ShippingAddressService {
    private final MemberRepository members;
    private final ShippingAddressRepository addresses;
    private final ShippingAddressBookRepository books;
    private final ShippingAddressRegistrationRepository registrations;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ShippingAddressResponse list(UserDetailsImpl user) { return view(member(user, false, null)); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingAddressResponse create(Create request, String rawKey, String account, UserDetailsImpl user) {
        Member member = member(user, true, account);
        Details d = normalize(request.address());
        String key = key(rawKey), fingerprint = fingerprint(d, request);
        var replay = registrations.findByMemberIdAndRequestKey(member.getId(), key);
        if (replay.isPresent()) {
            if (!replay.get().getFingerprint().equals(fingerprint)) throw new NanacocoaException(ADDRESS_CONFLICT);
            return view(member);
        }
        var book = book(member.getId());
        expect(book.getRevision(), request.expectedRevision());
        boolean first = !addresses.existsByMemberId(member.getId());
        var address = addresses.saveAndFlush(new ShippingAddress(member.getId(), d.label(), d.recipientName(),
            d.phoneNumber(), d.postalCode(), d.basicAddress(), d.detailAddress()));
        if (first || request.makeDefault()) book.designate(address.getId());
        book.changed(); books.save(book);
        registrations.save(new ShippingAddressRegistration(member.getId(), key, fingerprint));
        return view(member);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingAddressResponse update(Long id, Update request, String account, UserDetailsImpl user) {
        Member member = member(user, true, account);
        var row = owned(id, member.getId()); var book = book(member.getId());
        expect(book.getRevision(), request.expectedRevision()); expect(row.getVersion(), request.expectedVersion());
        Details d = normalize(request.address());
        row.change(d.label(), d.recipientName(), d.phoneNumber(), d.postalCode(), d.basicAddress(), d.detailAddress());
        book.changed(); books.save(book); addresses.flush();
        return view(member);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingAddressResponse delete(Long id, Long version, Long revision, String account, UserDetailsImpl user) {
        Member member = member(user, true, account);
        var row = owned(id, member.getId()); var book = book(member.getId());
        expect(book.getRevision(), revision); expect(row.getVersion(), version);
        if (Objects.equals(book.getDefaultAddressId(), id)) book.designate(null);
        book.changed(); books.save(book); addresses.delete(row); addresses.flush();
        return view(member);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingAddressResponse designate(Long id, Version request, String account, UserDetailsImpl user) {
        Member member = member(user, true, account);
        var row = owned(id, member.getId()); var book = book(member.getId());
        expect(book.getRevision(), request.expectedRevision()); expect(row.getVersion(), request.expectedVersion());
        book.designate(id); book.changed(); books.save(book);
        return view(member);
    }

    private Member member(UserDetailsImpl user, boolean lock, String account) {
        if (user == null) throw new NanacocoaException(AUTHENTICATION_REQUIRED);
        Member m;
        try {
            m = (lock ? members.findByEmailForUpdate(user.getUsername()) : members.findByEmail(user.getUsername()))
                .orElseThrow(() -> new NanacocoaException(AUTHENTICATION_REQUIRED));
        } catch (org.springframework.dao.PessimisticLockingFailureException conflict) {
            throw new NanacocoaException(ADDRESS_CONFLICT);
        }
        if (account != null && !m.checkoutAccount().equals(account)) throw new NanacocoaException(ORDER_CHECKOUT_ACCOUNT_CHANGED);
        return m;
    }
    private ShippingAddressBook book(Long member) {
        return books.findById(member).orElseGet(() -> new ShippingAddressBook(member));
    }
    private ShippingAddress owned(Long id, Long member) {
        return addresses.findByIdAndMemberId(id, member).orElseThrow(() -> new NanacocoaException(ADDRESS_NOT_FOUND));
    }
    private void expect(long actual, Long expected) {
        if (expected == null || expected < 0 || actual != expected) throw new NanacocoaException(ADDRESS_CONFLICT);
    }
    private ShippingAddressResponse view(Member member) {
        var book = book(member.getId());
        var rows = addresses.findByMemberIdOrderById(member.getId()).stream().map(a ->
            new ShippingAddressResponse.Item(a.getId(), a.getVersion(), a.getLabel(), a.getRecipientName(),
                a.getPhoneNumber(), a.getPostalCode(), a.getBasicAddress(), a.getDetailAddress(),
                Objects.equals(book.getDefaultAddressId(), a.getId()))).toList();
        return new ShippingAddressResponse(book.getRevision(), member.checkoutAccount(), rows);
    }
    private Details normalize(Details d) {
        if (d == null) throw new NanacocoaException(INVALID_REQUEST);
        String label = text(d.label(), 80, false), name = text(d.recipientName(), 100, false);
        String phone = text(d.phoneNumber(), 30, false), postal = text(d.postalCode(), 5, false);
        String basic = text(d.basicAddress(), 500, false), detail = text(d.detailAddress(), 500, true);
        if (!postal.matches("[0-9]{5}") || ("[" + postal + "] " + basic + (detail.isEmpty() ? "" : " " + detail)).length() > 500)
            throw new NanacocoaException(INVALID_REQUEST);
        return new Details(label, name, phone, postal, basic, detail);
    }
    private String text(String value, int max, boolean optional) {
        String s = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if ((!optional && s.isBlank()) || s.length() > max || s.chars().anyMatch(Character::isISOControl))
            throw new NanacocoaException(INVALID_REQUEST);
        return s;
    }
    private String key(String raw) {
        if (raw == null || !raw.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new NanacocoaException(INVALID_REQUEST);
        return UUID.fromString(raw).toString();
    }
    private String fingerprint(Details d, Create request) {
        StringBuilder canonical = new StringBuilder();
        for (String field : List.of(d.label(), d.recipientName(), d.phoneNumber(), d.postalCode(),
                                   d.basicAddress(), d.detailAddress(), String.valueOf(request.makeDefault()), String.valueOf(request.expectedRevision())))
            canonical.append(field.length()).append(':').append(field);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
