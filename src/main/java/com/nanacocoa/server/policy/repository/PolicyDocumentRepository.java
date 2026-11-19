package com.nanacocoa.server.policy.repository;

import com.nanacocoa.server.policy.entity.PolicyDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, Long> {
    List<PolicyDocument> findByActiveTrueOrderByDocumentCodeAsc();
    Optional<PolicyDocument> findByDocumentCodeAndDocumentVersion(String code, String version);
    boolean existsByDocumentCode(String code);
}
