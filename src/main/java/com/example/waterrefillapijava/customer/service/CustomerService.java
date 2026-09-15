package com.example.waterrefillapijava.customer.service;

import java.math.BigDecimal;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.shared.exception.ConflictException;
import com.example.waterrefillapijava.shared.exception.NotFoundException;
import java.time.LocalDateTime;

import com.example.waterrefillapijava.customer.model.Customer;
import com.example.waterrefillapijava.customer.repository.CustomerRepository;
import com.example.waterrefillapijava.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerService {

	private final CustomerRepository customerRepository;
	private final OrderRepository orderRepository;

	@Transactional(readOnly = true)
	public Page<Customer> search(String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return customerRepository.findByNameContainingIgnoreCaseAndDeletedFalse(search, pageable);
		}
		return customerRepository.findByDeletedFalse(pageable);
	}

	@Transactional(readOnly = true)
	public Customer findById(Long id) {
		final Customer customer = customerRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Customer not found."));
		if (customer.isDeleted()) {
			throw new NotFoundException("Customer not found.");
		}
		return customer;
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
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:debt-aging")
	})
	public void delete(Long id) {
		final Customer customer = customerRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Customer not found."));
		if (customer.isDeleted()) {
			throw new ConflictException("Customer is already archived.");
		}
		customer.setDeleted(true);
		customer.setDeletedAt(LocalDateTime.now());
		customerRepository.save(customer);
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

	@Transactional(readOnly = true)
	public Page<Customer> archiveList(String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return customerRepository.findByNameContainingIgnoreCaseAndDeletedTrue(search, pageable);
		}
		return customerRepository.findByDeletedTrue(pageable);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:debt-aging")
	})
	public void restore(Long id) {
		final Customer customer = customerRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Customer not found."));
		if (!customer.isDeleted()) {
			throw new ConflictException("Customer is not archived.");
		}
		if (customerRepository.existsByPhoneAndIdNot(customer.getPhone(), id)) {
			throw new ConflictException("A customer with this phone already exists.");
		}
		if (customerRepository.existsByEmailAndIdNot(customer.getEmail(), id)) {
			throw new ConflictException("A customer with this email already exists.");
		}
		customer.setDeleted(false);
		customer.setDeletedAt(null);
		customerRepository.save(customer);
	}

	@Transactional
	public void permanentDelete(Long id) {
		final Customer customer = customerRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Customer not found."));
		if (!customer.isDeleted()) {
			throw new ConflictException("Customer must be archived before permanent deletion.");
		}
		if (orderRepository.existsByCustomerId(id)) {
			throw new ConflictException("Cannot permanently delete: customer has orders.");
		}
		customerRepository.deleteById(id);
	}
}
