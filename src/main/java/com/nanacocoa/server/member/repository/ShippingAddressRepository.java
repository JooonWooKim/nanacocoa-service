package com.nanacocoa.server.member.repository;

import com.nanacocoa.server.member.entity.ShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {
    List<ShippingAddress> findByMemberIdOrderById(Long memberId);
    Optional<ShippingAddress> findByIdAndMemberId(Long id, Long memberId);
    boolean existsByMemberId(Long memberId);
}
