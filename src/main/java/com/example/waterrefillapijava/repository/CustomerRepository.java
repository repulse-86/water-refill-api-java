package com.example.waterrefillapijava.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.waterrefillapijava.model.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

	boolean existsByPhone(String phone);

	boolean existsByPhoneAndIdNot(String phone, Long id);

	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, Long id);

	Page<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);

	Optional<Customer> findByNameIgnoreCase(String name);

	long countBySubscriberStatus(String subscriberStatus);

	@Query("SELECT c FROM Customer c WHERE c.outstandingBalance > 0 OR c.bottleDebt > 0 ORDER BY c.outstandingBalance DESC")
	List<Customer> findWithDebt();
}
