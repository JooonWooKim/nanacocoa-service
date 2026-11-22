package com.nanacocoa.server.recovery.repository;

import com.nanacocoa.server.recovery.entity.RecoveryLimit;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RecoveryLimitRepository extends JpaRepository<RecoveryLimit, Long> {
    @Modifying
    @Query(value = "insert into password_recovery_limits (limit_key, window_start, attempts, next_allowed_at) "
            + "values (:key, :now, 0, :now) on duplicate key update limit_key = limit_key", nativeQuery = true)
    void ensureRow(@Param("key") String key, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecoveryLimit r where r.limitKey = :key")
    RecoveryLimit lockByKey(@Param("key") String key);

    @Modifying
    @Query("delete from RecoveryLimit r where r.windowStart < :before")
    void deleteExpired(@Param("before") LocalDateTime before);
}
