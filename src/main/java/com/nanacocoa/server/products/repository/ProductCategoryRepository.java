package com.nanacocoa.server.products.repository;

import com.nanacocoa.server.products.entity.ProductCategory;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    List<ProductCategory> findAllByOrderBySortOrderAscIdAsc();
    List<ProductCategory> findByVisibleTrueOrderBySortOrderAscIdAsc();
    Optional<ProductCategory> findFirstByOrderBySortOrderDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ProductCategory c where c.id = :id")
    Optional<ProductCategory> findForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from ProductCategory c where c.id = :id")
    Optional<ProductCategory> findForSearch(@Param("id") Long id);
}
