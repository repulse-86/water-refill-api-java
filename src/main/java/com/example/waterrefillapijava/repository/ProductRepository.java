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

	@Query("SELECT p FROM Product p WHERE p.stockQuantity <= p.reorderPoint ORDER BY p.stockQuantity ASC")
	List<Product> findLowStock();

	@Modifying
	@Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty WHERE p.id = :id AND p.stockQuantity >= :qty")
	int decrementStockIfAvailable(@Param("id") Long id, @Param("qty") int qty);

	@Modifying
	@Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :qty WHERE p.id = :id")
	void incrementStock(@Param("id") Long id, @Param("qty") int qty);
}
