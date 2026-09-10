package com.example.waterrefillapijava.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductType;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

	Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

	Page<Product> findByType(ProductType type, Pageable pageable);

	Page<Product> findByNameContainingIgnoreCaseAndType(String name, ProductType type, Pageable pageable);
}
