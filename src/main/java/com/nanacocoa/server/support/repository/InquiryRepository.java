package com.nanacocoa.server.support.repository;

import com.nanacocoa.server.support.entity.Inquiry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    Optional<Inquiry> findByIdAndMemberId(Long id, Long memberId);
    Optional<Inquiry> findByMemberIdAndRequestKey(Long memberId, String requestKey);
    Slice<Inquiry> findByMemberId(Long memberId, Pageable page);
    Slice<Inquiry> findByStatus(Inquiry.Status status, Pageable page);
    @Query("select i from Inquiry i")
    Slice<Inquiry> findAllInquiries(Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inquiry i where i.id = :id")
    Optional<Inquiry> findForUpdate(@Param("id") Long id);
}
