package com.nanacocoa.server.recovery.repository;

import com.nanacocoa.server.recovery.entity.RecoveryToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RecoveryTokenRepository extends JpaRepository<RecoveryToken, Long> {
    Optional<RecoveryToken> findByTokenHash(String hash);
    @Query("select t.memberId from RecoveryToken t where t.tokenHash = :hash")
    Optional<Long> findMemberIdByHash(@Param("hash") String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RecoveryToken> findByMemberIdAndConsumedFalse(Long memberId);
}
