package com.nanacocoa.server.products.repository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import com.nanacocoa.server.products.entity.Products;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductsRepository extends JpaRepository<Products, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Products p where p.id = :id")
	Optional<Products> findByIdForUpdate(@Param("id") Long id);

	boolean existsByName(String name);

	List<Products> findAllByOrderByCreatedAtDescIdDesc();
}
