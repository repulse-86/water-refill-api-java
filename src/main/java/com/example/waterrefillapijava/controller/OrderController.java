package com.example.waterrefillapijava.controller;

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

import com.example.waterrefillapijava.dto.DeliveryRequest;
import com.example.waterrefillapijava.dto.MessageResponse;
import com.example.waterrefillapijava.dto.OrderRequest;
import com.example.waterrefillapijava.dto.OrderResponse;
import com.example.waterrefillapijava.dto.OrderUpdateRequest;
import com.example.waterrefillapijava.dto.PageResponse;
import com.example.waterrefillapijava.dto.StatusRequest;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;
import com.example.waterrefillapijava.service.OrderFulfillmentService;
import com.example.waterrefillapijava.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

	private final OrderService orderService;
	private final OrderFulfillmentService orderFulfillmentService;

	@GetMapping
	public ResponseEntity<PageResponse<OrderResponse>> list(
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search,
		@RequestParam(required = false) OrderType orderType,
		@RequestParam(required = false) OrderStatus status
	) {
		final Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, size)),
			Sort.by("id").descending());

		final Page<Order> orders = orderService.search(search, orderType, status, pageable);

		return ResponseEntity.ok(toPageResponse(orders));
	}

	@GetMapping("/{id}")
	public ResponseEntity<OrderResponse> get(@PathVariable final Long id) {
		final Order order = orderService.findById(id);
		return ResponseEntity.ok(orderService.toResponse(order));
	}

	@PostMapping
	public ResponseEntity<OrderResponse> create(@Valid @RequestBody final OrderRequest request) {
		final Order order = orderService.create(
			request.customerId(), request.orderType(), request.paymentMethod(),
			request.totalAmount(), request.amountPaid(), request.deliveryFee(),
			request.notes(), request.deliveryAddress(), request.bottlesReturned(),
			request.items()
		);

		log.info("Order created: id={}, type={}, status={}", order.getId(), order.getOrderType(), order.getStatus());

		return ResponseEntity.ok(orderService.toResponse(order));
	}

	@PutMapping("/{id}")
	public ResponseEntity<OrderResponse> update(
		@PathVariable final Long id,
		@Valid @RequestBody final OrderUpdateRequest request
	) {
		final Order order = orderService.update(
			id, request.customerId(), request.orderType(), request.status(),
			request.paymentMethod(), request.totalAmount(), request.amountPaid(),
			request.deliveryFee(), request.notes(), request.deliveryAddress(),
			request.deliveryStatus(), request.items()
		);

		log.info("Order updated: id={}, status={}", order.getId(), order.getStatus());

		return ResponseEntity.ok(orderService.toResponse(order));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable final Long id) {
		orderService.delete(id);

		log.info("Order deleted: id={}", id);

		return ResponseEntity.ok(new MessageResponse("Order deleted successfully."));
	}

	@PostMapping("/{id}/status")
	public ResponseEntity<OrderResponse> advanceStatus(
		@PathVariable final Long id,
		@Valid @RequestBody final StatusRequest request
	) {
		final Order order = orderFulfillmentService.advanceStatus(id, request.status());

		log.info("Order status advanced: id={}, status={}", order.getId(), order.getStatus());

		return ResponseEntity.ok(orderService.toResponse(order));
	}

	@PostMapping("/{id}/delivery")
	public ResponseEntity<OrderResponse> recordDelivery(
		@PathVariable final Long id,
		@Valid @RequestBody final DeliveryRequest request
	) {
		final Order order = orderFulfillmentService.recordDelivery(
			id, request.deliveryStatus(), request.bottlesReturned(), request.cashCollected()
		);

		log.info("Delivery recorded: id={}, status={}", order.getId(), order.getDeliveryStatus());

		return ResponseEntity.ok(orderService.toResponse(order));
	}

	private PageResponse<OrderResponse> toPageResponse(final Page<Order> page) {
		return new PageResponse<>(
			page.getContent().stream().map(orderService::toResponse).toList(),
			page.getNumber() + 1,
			page.getSize(),
			page.getTotalElements(),
			page.getTotalPages()
		);
	}
}
