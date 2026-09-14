package com.example.waterrefillapijava.controller;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.waterrefillapijava.dto.CustomerRequest;
import com.example.waterrefillapijava.dto.CustomerResponse;
import com.example.waterrefillapijava.dto.CustomerUpdateRequest;
import com.example.waterrefillapijava.dto.MessageResponse;
import com.example.waterrefillapijava.dto.PageResponse;
import com.example.waterrefillapijava.dto.SettleCustomerRequest;
import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.service.CustomerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

	private final CustomerService customerService;

	@GetMapping
	public ResponseEntity<PageResponse<CustomerResponse>> list(
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search
	) {
		final Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, size)),
			Sort.by("name").ascending());

		final Page<Customer> customers = customerService.search(search, pageable);

		return ResponseEntity.ok(toPageResponse(customers));
	}

	@GetMapping("/{id}")
	public ResponseEntity<CustomerResponse> get(@PathVariable final Long id) {
		final Customer customer = customerService.findById(id);
		return ResponseEntity.ok(toCustomerResponse(customer));
	}

	@PostMapping
	public ResponseEntity<CustomerResponse> create(@Valid @RequestBody final CustomerRequest request) {
		final Customer customer = customerService.create(
			request.name(), request.phone(), request.email()
		);

		log.info("Customer created: id={}, name={}", customer.getId(), customer.getName());

		return ResponseEntity.ok(toCustomerResponse(customer));
	}

	@PutMapping("/{id}")
	public ResponseEntity<CustomerResponse> update(
		@PathVariable final Long id,
		@Valid @RequestBody final CustomerUpdateRequest request
	) {
		final Customer customer = customerService.update(
			id, request.name(), request.phone(), request.email(), request.subscriberStatus(),
			request.bottleDebt(), request.outstandingBalance()
		);

		log.info("Customer updated: id={}, name={}", customer.getId(), customer.getName());

		return ResponseEntity.ok(toCustomerResponse(customer));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable final Long id) {
		customerService.delete(id);

		log.info("Customer deleted: id={}", id);

		return ResponseEntity.ok(new MessageResponse("Customer deleted successfully."));
	}

	@GetMapping("/deleted")
	public ResponseEntity<PageResponse<CustomerResponse>> listDeleted(
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search
	) {
		final Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, size)),
			Sort.by("id").descending());

		final Page<Customer> customers = customerService.archiveList(search, pageable);

		return ResponseEntity.ok(toPageResponse(customers));
	}

	@PostMapping("/{id}/restore")
	public ResponseEntity<CustomerResponse> restore(@PathVariable final Long id) {
		customerService.restore(id);

		log.info("Customer restored: id={}", id);

		return ResponseEntity.ok(toCustomerResponse(customerService.findById(id)));
	}

	@DeleteMapping("/{id}/permanent")
	public ResponseEntity<?> permanentDelete(@PathVariable final Long id) {
		customerService.permanentDelete(id);

		log.info("Customer permanently deleted: id={}", id);

		return ResponseEntity.ok(new MessageResponse("Customer permanently deleted successfully."));
	}

	@PostMapping("/{id}/settle")
	public ResponseEntity<CustomerResponse> settle(
		@PathVariable final Long id,
		@Valid @RequestBody final SettleCustomerRequest request
	) {
		final int bottleReturn = request.bottleReturn() != null ? request.bottleReturn() : 0;
		final BigDecimal cashPayment = request.cashPayment() != null ? request.cashPayment() : BigDecimal.ZERO;

		final Customer customer = customerService.settle(id, bottleReturn, cashPayment);

		log.info("Customer settled: id={}, bottleReturn={}, cashPayment={}", id, bottleReturn, cashPayment);

		return ResponseEntity.ok(toCustomerResponse(customer));
	}

	private PageResponse<CustomerResponse> toPageResponse(final Page<Customer> page) {
		return new PageResponse<>(
			page.getContent().stream().map(this::toCustomerResponse).toList(),
			page.getNumber() + 1,
			page.getSize(),
			page.getTotalElements(),
			page.getTotalPages()
		);
	}

	private CustomerResponse toCustomerResponse(final Customer customer) {
		return new CustomerResponse(
			customer.getId(),
			customer.getName(),
			customer.getPhone(),
			customer.getEmail(),
			customer.getSubscriberStatus(),
			customer.getBottleDebt(),
			customer.getOutstandingBalance(),
			customer.getDeletedAt() != null ? customer.getDeletedAt().toString() : null
		);
	}
}
