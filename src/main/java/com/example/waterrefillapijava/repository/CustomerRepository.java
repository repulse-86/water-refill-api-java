package com.example.waterrefillapijava.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

	@Modifying
	@Query("UPDATE Customer c SET c.outstandingBalance = c.outstandingBalance + :amount WHERE c.id = :id")
	void adjustBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

	@Modifying
	@Query("UPDATE Customer c SET c.bottleDebt = c.bottleDebt - :qty WHERE c.id = :id")
	void decrementBottleDebt(@Param("id") Long id, @Param("qty") int qty);

	// User-facing (deleted = false)
	Page<Customer> findByDeletedFalse(Pageable pageable);

	Page<Customer> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);

	long countBySubscriberStatusAndDeletedFalse(String subscriberStatus);

	// Archive (deleted = true)
	Page<Customer> findByDeletedTrue(Pageable pageable);

	Page<Customer> findByNameContainingIgnoreCaseAndDeletedTrue(String name, Pageable pageable);
}
