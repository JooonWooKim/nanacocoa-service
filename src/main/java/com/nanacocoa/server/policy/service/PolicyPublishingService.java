package com.nanacocoa.server.policy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.policy.entity.*;
import com.nanacocoa.server.policy.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static com.nanacocoa.server.common.exception.ErrorCode.POLICY_UNAVAILABLE;

@Service @RequiredArgsConstructor
public class PolicyPublishingService {
    private final PolicyDocumentRepository documents;
    private final PolicyPublicationRepository publications;
    private final ObjectMapper json;
    private static final Set<String> CODES = Set.of("TERMS", "PRIVACY", "SELLER_INFO", "SHIPPING_RETURN", "EMAIL_RECEIVE");

    @Transactional
    public void initialize() {
        if (!publications.existsById(1L)) publications.saveAndFlush(new PolicyPublication());
    }

    @Transactional
    public void publish(PolicyCatalog catalog) {
        var publication = publications.lockForPublication().orElseThrow(() -> new NanacocoaException(POLICY_UNAVAILABLE));
        if (catalog == null || catalog.documents() == null) throw new NanacocoaException(POLICY_UNAVAILABLE);
        // 빈 기본 자료는 이미 게시된 정책을 지우거나 이전 버전으로 되돌리지 않는다.
        if (catalog.revision() == 0 && catalog.documents().isEmpty()) return;
        validate(catalog);
        String hash = hash(catalog);
        if (catalog.revision() < publication.getRevision()) return;
        if (catalog.revision() == publication.getRevision()) {
            if (!hash.equals(publication.getContentHash())) throw new NanacocoaException(POLICY_UNAVAILABLE);
            return;
        }
        var next = new ArrayList<PolicyDocument>();
        for (var d : catalog.documents()) {
            String documentHash = hash(d);
            PolicyDocument stored = documents.findByDocumentCodeAndDocumentVersion(d.documentCode(), d.version()).orElse(null);
            if (stored != null && !documentHash.equals(stored.getContentHash())) throw new NanacocoaException(POLICY_UNAVAILABLE);
            if (stored == null) stored = documents.save(new PolicyDocument(d.documentCode(), d.version(), d.title(), d.body(),
                    d.consentMode(), d.effectiveAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(), d.approvedBy(),
                    d.approvedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(), documentHash));
            next.add(stored);
        }
        documents.findByActiveTrueOrderByDocumentCodeAsc().forEach(d -> d.activate(false));
        next.forEach(d -> d.activate(true));
        publication.publish(catalog.revision(), hash);
    }

    private void validate(PolicyCatalog catalog) {
        if (catalog.revision() < 1 || catalog.documents().size() != CODES.size()) throw new NanacocoaException(POLICY_UNAVAILABLE);
        var codes = new HashSet<String>();
        boolean required = false;
        for (var d : catalog.documents()) {
            if (d == null || d.documentCode() == null || !CODES.contains(d.documentCode()) || !codes.add(d.documentCode())
                    || !bounded(d.version(),64) || !d.version().matches("[A-Za-z0-9._-]+")
                    || !bounded(d.title(),200) || !bounded(d.body(),200000) || !bounded(d.approvedBy(),100)
                    || d.effectiveAt() == null || d.approvedAt() == null || d.consentMode() == null
                    || d.effectiveAt().isAfter(OffsetDateTime.now()) || d.approvedAt().isAfter(OffsetDateTime.now())
                    || d.approvedAt().isAfter(d.effectiveAt())) throw new NanacocoaException(POLICY_UNAVAILABLE);
            if (d.documentCode().equals("EMAIL_RECEIVE") != (d.consentMode() == ConsentMode.OPTIONAL_EMAIL)) throw new NanacocoaException(POLICY_UNAVAILABLE);
            if (Set.of("SELLER_INFO", "SHIPPING_RETURN").contains(d.documentCode()) && d.consentMode() != ConsentMode.NONE) throw new NanacocoaException(POLICY_UNAVAILABLE);
            required |= d.consentMode() == ConsentMode.REQUIRED;
        }
        if (!required) throw new NanacocoaException(POLICY_UNAVAILABLE);
    }
    private boolean bounded(String value, int max) { return value != null && !value.isBlank() && value.length() <= max; }
    private String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8))); }
        catch (JsonProcessingException | NoSuchAlgorithmException e) { throw new IllegalStateException("Policy digest unavailable"); }
    }
}
