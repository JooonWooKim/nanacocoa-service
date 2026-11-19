package com.nanacocoa.server.policy.repository;

import com.nanacocoa.server.policy.entity.PolicyPublication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface PolicyPublicationRepository extends JpaRepository<PolicyPublication, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PolicyPublication p where p.id = 1")
    Optional<PolicyPublication> lockForPublication();
    @Lock(LockModeType.PESSIMISTIC_READ) @Query("select p from PolicyPublication p where p.id = 1")
    Optional<PolicyPublication> lockForSignup();
}
