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

    String SEARCH_FILTER = " where (:category is null or p.categoryId = :category)"
            + " and (:term = '' or locate(:term, lower(p.name)) > 0)";

    @Query("select count(p) from Products p" + SEARCH_FILTER)
    long countSearch(@Param("term") String term, @Param("category") Long category);

    @Query("select p from Products p" + SEARCH_FILTER + " order by p.createdAt desc, p.id desc")
    List<Products> search(@Param("term") String term, @Param("category") Long category,
            org.springframework.data.domain.Pageable page);

	boolean existsByName(String name);

	List<Products> findAllByOrderByCreatedAtDescIdDesc();
}
