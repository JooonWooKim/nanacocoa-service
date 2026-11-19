package com.nanacocoa.server.policy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.policy.entity.ConsentMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;
import static com.nanacocoa.server.common.exception.ErrorCode.POLICY_UNAVAILABLE;

@Component @RequiredArgsConstructor
public class PolicyFileStore {
    private final ObjectMapper json;
    private volatile Snapshot loaded;
    private static final Set<String> CODES = Set.of("TERMS", "PRIVACY", "SELLER_INFO", "SHIPPING_RETURN", "EMAIL_RECEIVE");

    // 시작 시 단 한 번 설치한다. 실패한 후보는 공개하지 않으며 실행 중 교체는 지원하지 않는다.
    public synchronized void initialize(PolicyCatalog catalog) {
        if (loaded != null) throw new NanacocoaException(POLICY_UNAVAILABLE);
        if (catalog == null) unavailable();
        var all = new HashMap<String, PolicyCatalog.Document>();
        for (var d : catalog.archivedDocuments()) add(all, d);
        var archives = Map.copyOf(all);
        try {
            if (catalog.documents() == null || catalog.revision() < 0
                    || (catalog.revision() == 0 && !catalog.documents().isEmpty())
                    || (catalog.revision() > 0 && catalog.documents().size() != CODES.size())) unavailable();
            var currentCodes = new HashSet<String>();
            for (var d : catalog.documents()) {
                add(all, d);
                if (!currentCodes.add(d.documentCode())) unavailable();
            }
            if (catalog.revision() > 0 && catalog.documents().stream().noneMatch(d -> d.consentMode() == ConsentMode.REQUIRED)) unavailable();
        } catch (NanacocoaException e) {
            // 현재 집합이 잘못돼도 독립적으로 검증된 과거 원문은 읽을 수 있다.
            loaded = new Snapshot(0, List.of(), archives);
            throw e;
        }
        loaded = new Snapshot(catalog.revision(), List.copyOf(catalog.documents()).stream()
                .sorted(Comparator.comparing(PolicyCatalog.Document::documentCode)).toList(), Map.copyOf(all));
    }
    private void add(Map<String, PolicyCatalog.Document> all, PolicyCatalog.Document d) {
        validate(d);
        if (all.putIfAbsent(d.documentCode() + "/" + d.version(), d) != null) unavailable();
    }
    private void validate(PolicyCatalog.Document d) {
        if (d == null || d.documentCode() == null || !CODES.contains(d.documentCode()) || !bounded(d.version(),64)
                || !d.version().matches("[A-Za-z0-9._-]+") || !bounded(d.title(),200)
                || !bounded(d.body(),200000) || !bounded(d.approvedBy(),100)
                || d.effectiveAt() == null || d.approvedAt() == null || d.consentMode() == null
                || d.effectiveAt().isAfter(OffsetDateTime.now()) || d.approvedAt().isAfter(OffsetDateTime.now())
                || d.approvedAt().isAfter(d.effectiveAt())) unavailable();
        if (d.documentCode().equals("EMAIL_RECEIVE") != (d.consentMode() == ConsentMode.OPTIONAL_EMAIL)) unavailable();
        if (Set.of("SELLER_INFO", "SHIPPING_RETURN").contains(d.documentCode()) && d.consentMode() != ConsentMode.NONE) unavailable();
    }
    private boolean bounded(String value, int max) { return value != null && !value.isBlank() && value.length() <= max; }
    private void unavailable() { throw new NanacocoaException(POLICY_UNAVAILABLE); }
    public Snapshot snapshot() { return loaded == null ? new Snapshot(0, List.of(), Map.of()) : loaded; }
    public String hash(PolicyCatalog.Document document) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(document).getBytes(StandardCharsets.UTF_8))); }
        catch (JsonProcessingException | NoSuchAlgorithmException e) { throw new IllegalStateException("Policy digest unavailable"); }
    }
    public record Snapshot(long revision, List<PolicyCatalog.Document> current, Map<String, PolicyCatalog.Document> all) {
        public Optional<PolicyCatalog.Document> find(String code, String version) { return Optional.ofNullable(all.get(code + "/" + version)); }
    }
}
