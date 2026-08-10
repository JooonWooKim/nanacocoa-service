package com.nanacocoa.server.products.repository;

import com.nanacocoa.server.products.entity.Products;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductsRepository extends JpaRepository<Products, Long> {
	boolean existsByName(String name);

	List<Products> findAllByOrderByCreatedAtDescIdDesc();
}
