package com.example.waterrefillapijava.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductType;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

	Optional<Product> findByNameIgnoreCase(String name);

	Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

	Page<Product> findByType(ProductType type, Pageable pageable);

	Page<Product> findByNameContainingIgnoreCaseAndType(String name, ProductType type, Pageable pageable);

	@Query("SELECT p FROM Product p WHERE p.stockQuantity <= p.reorderPoint AND p.deleted = false ORDER BY p.stockQuantity ASC")
	List<Product> findLowStock();

	@Modifying
	@Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty WHERE p.id = :id AND p.stockQuantity >= :qty")
	int decrementStockIfAvailable(@Param("id") Long id, @Param("qty") int qty);

	@Modifying
	@Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :qty WHERE p.id = :id")
	void incrementStock(@Param("id") Long id, @Param("qty") int qty);

	// User-facing (deleted = false)
	Page<Product> findByDeletedFalse(Pageable pageable);

	Page<Product> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);

	Page<Product> findByTypeAndDeletedFalse(ProductType type, Pageable pageable);

	Page<Product> findByNameContainingIgnoreCaseAndTypeAndDeletedFalse(String name, ProductType type, Pageable pageable);

	// Archive (deleted = true)
	Page<Product> findByDeletedTrue(Pageable pageable);

	Page<Product> findByNameContainingIgnoreCaseAndDeletedTrue(String name, Pageable pageable);

	Page<Product> findByTypeAndDeletedTrue(ProductType type, Pageable pageable);

	Page<Product> findByNameContainingIgnoreCaseAndTypeAndDeletedTrue(String name, ProductType type, Pageable pageable);
}
