package com.nanacocoa.server.member.repository;
import com.nanacocoa.server.member.entity.ShippingAddressRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ShippingAddressRegistrationRepository extends JpaRepository<ShippingAddressRegistration, Long> {
    Optional<ShippingAddressRegistration> findByMemberIdAndRequestKey(Long memberId, String key);
}
