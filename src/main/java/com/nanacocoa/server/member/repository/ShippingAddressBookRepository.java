package com.nanacocoa.server.member.repository;
import com.nanacocoa.server.member.entity.ShippingAddressBook;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ShippingAddressBookRepository extends JpaRepository<ShippingAddressBook, Long> {}
