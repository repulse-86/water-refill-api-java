package com.example.waterrefillapijava.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.waterrefillapijava.model.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

	boolean existsByPhone(String phone);

	boolean existsByPhoneAndIdNot(String phone, Long id);

	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, Long id);

	Page<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
