package com.example.waterrefillapijava.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.waterrefillapijava.model.ProductComponent;

@Repository
public interface ProductComponentRepository extends JpaRepository<ProductComponent, Long> {

	Page<ProductComponent> findByProductId(Long productId, Pageable pageable);

	Page<ProductComponent> findByProductIdAndComponent_NameContainingIgnoreCase(Long productId, String name, Pageable pageable);

	Optional<ProductComponent> findByProductIdAndComponentId(Long productId, Long componentId);

	boolean existsByProductIdAndComponentId(Long productId, Long componentId);

	void deleteByProductIdAndComponentId(Long productId, Long componentId);

	@Modifying
	@Query("DELETE FROM ProductComponent pc WHERE pc.product.id = :productId")
	void deleteByProductId(@Param("productId") Long productId);

	@Modifying
	@Query("DELETE FROM ProductComponent pc WHERE pc.component.id = :componentId")
	void deleteByComponentId(@Param("componentId") Long componentId);
}
