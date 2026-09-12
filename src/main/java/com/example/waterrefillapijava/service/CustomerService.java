package com.example.waterrefillapijava.service;

import java.math.BigDecimal;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.exception.ConflictException;
import com.example.waterrefillapijava.exception.NotFoundException;
import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerService {

	private final CustomerRepository customerRepository;

	@Transactional(readOnly = true)
	public Page<Customer> search(String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return customerRepository.findByNameContainingIgnoreCase(search, pageable);
		}
		return customerRepository.findAll(pageable);
	}

	@Transactional(readOnly = true)
	public Customer findById(Long id) {
		return customerRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Customer not found."));
	}

	@Transactional
	public Customer create(String name, String phone, String email) {
		if (customerRepository.existsByPhone(phone)) {
			throw new ConflictException("The phone has already been taken.");
		}
		if (customerRepository.existsByEmail(email)) {
			throw new ConflictException("The email has already been taken.");
		}

		final Customer customer = Customer.builder()
			.name(name)
			.phone(phone)
			.email(email)
			.subscriberStatus("active")
			.bottleDebt(0)
			.outstandingBalance(BigDecimal.ZERO)
			.build();

		return customerRepository.save(customer);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:debt-aging")
	})
	public Customer update(Long id, String name, String phone, String email, String subscriberStatus,
			Integer bottleDebt, BigDecimal outstandingBalance) {
		final Customer customer = findById(id);

		if (!customer.getPhone().equals(phone) && customerRepository.existsByPhoneAndIdNot(phone, id)) {
			throw new ConflictException("The phone has already been taken.");
		}
		if (!customer.getEmail().equals(email) && customerRepository.existsByEmailAndIdNot(email, id)) {
			throw new ConflictException("The email has already been taken.");
		}

		customer.setName(name);
		customer.setPhone(phone);
		customer.setEmail(email);
		customer.setSubscriberStatus(subscriberStatus);
		customer.setBottleDebt(bottleDebt);
		customer.setOutstandingBalance(outstandingBalance);

		return customerRepository.save(customer);
	}

	@Transactional
	public void delete(Long id) {
		if (!customerRepository.existsById(id)) {
			throw new NotFoundException("Customer not found.");
		}
		customerRepository.deleteById(id);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:debt-aging")
	})
	public Customer settle(Long id, int bottleReturn, BigDecimal cashPayment) {
		final Customer customer = findById(id);

		final int newBottleDebt = Math.max(0, customer.getBottleDebt() - bottleReturn);
		final BigDecimal newBalance = customer.getOutstandingBalance()
			.subtract(cashPayment)
			.max(BigDecimal.ZERO);

		customer.setBottleDebt(newBottleDebt);
		customer.setOutstandingBalance(newBalance);

		return customerRepository.save(customer);
	}
}
