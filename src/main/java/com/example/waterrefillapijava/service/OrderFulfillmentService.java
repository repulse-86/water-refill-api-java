package com.example.waterrefillapijava.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.dto.BoardOrderResponse;
import com.example.waterrefillapijava.dto.DeliveryRequest;
import com.example.waterrefillapijava.dto.FulfillmentBoardResponse;
import com.example.waterrefillapijava.exception.FieldValidationException;
import com.example.waterrefillapijava.exception.NotFoundException;
import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.DeliveryStatus;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderFulfillmentService {

	private final OrderRepository orderRepository;
	private final CustomerRepository customerRepository;

	private static final Map<OrderType, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
		OrderType.walk_in, Set.of(OrderStatus.queued, OrderStatus.processing, OrderStatus.completed),
		OrderType.delivery, Set.of(OrderStatus.queued, OrderStatus.processing, OrderStatus.transit, OrderStatus.completed)
	);

	@Transactional(readOnly = true)
	public FulfillmentBoardResponse getBoard() {
		final List<OrderStatus> boardStatuses = List.of(
			OrderStatus.queued, OrderStatus.processing, OrderStatus.transit, OrderStatus.completed
		);

		final List<BoardOrderResponse> all = orderRepository.findAllForBoardGrouped(boardStatuses);

		final Map<OrderStatus, List<BoardOrderResponse>> grouped = new EnumMap<>(OrderStatus.class);
		Arrays.stream(OrderStatus.values())
			.forEach(s -> grouped.put(s, new ArrayList<>()));

		for (BoardOrderResponse order : all) {
			grouped.get(order.status()).add(order);
		}

		return new FulfillmentBoardResponse(grouped);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict(value = "report:daily-sales", allEntries = true),
		@CacheEvict(value = "report:product-performance", allEntries = true),
		@CacheEvict(value = "report:debt-aging", allEntries = true),
		@CacheEvict(value = "report:reconciliation", allEntries = true)
	})
	public Order advanceStatus(final Long id, final OrderStatus newStatus) {
		final Order order = orderRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Order not found."));

		if (order.isDeleted()) {
			throw new NotFoundException("Order not found.");
		}

		final Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(order.getOrderType(), Set.of());
		if (!allowed.contains(newStatus)) {
			throw FieldValidationException.builder()
				.add("status", "Cannot transition from '" + order.getStatus() + "' to '" + newStatus + "' for " + order.getOrderType() + " orders.")
				.build();
		}

		if (!isValidTransition(order.getOrderType(), order.getStatus(), newStatus)) {
			throw FieldValidationException.builder()
				.add("status", "Invalid status transition from '" + order.getStatus() + "' to '" + newStatus + "'.")
				.build();
		}

		order.setStatus(newStatus);

		if (newStatus == OrderStatus.completed && order.getOrderType() == OrderType.delivery) {
			order.setDeliveryStatus(DeliveryStatus.delivered);
			order.setDeliveredAt(LocalDateTime.now());
		}

		return orderRepository.save(order);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict(value = "report:daily-sales", allEntries = true),
		@CacheEvict(value = "report:product-performance", allEntries = true),
		@CacheEvict(value = "report:debt-aging", allEntries = true),
		@CacheEvict(value = "report:reconciliation", allEntries = true)
	})
	public Order recordDelivery(final Long id, final DeliveryStatus deliveryStatus,
			final Integer bottlesReturned, final BigDecimal cashCollected) {
		final Order order = orderRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Order not found."));

		if (order.isDeleted()) {
			throw new NotFoundException("Order not found.");
		}

		if (order.getOrderType() != OrderType.delivery) {
			throw FieldValidationException.builder()
				.add("order_type", "Delivery recording is only available for delivery orders.")
				.build();
		}

		order.setDeliveryStatus(deliveryStatus);
		order.setDeliveredAt(LocalDateTime.now());

		if (bottlesReturned != null && bottlesReturned > 0 && order.getCustomer() != null) {
			final Customer customer = order.getCustomer();
			customer.setBottleDebt(Math.max(0, customer.getBottleDebt() - bottlesReturned));
			customerRepository.save(customer);
			order.setBottlesReturnedAtDelivery(bottlesReturned);
		}

		if (cashCollected != null && cashCollected.compareTo(BigDecimal.ZERO) > 0 && order.getCustomer() != null) {
			final Customer customer = order.getCustomer();
			customer.setOutstandingBalance(customer.getOutstandingBalance().subtract(cashCollected));
			customerRepository.save(customer);
			order.setCashCollectedAtDelivery(cashCollected);
		}

		if (deliveryStatus == DeliveryStatus.delivered) {
			order.setStatus(OrderStatus.completed);
		}

		return orderRepository.save(order);
	}

	private boolean isValidTransition(final OrderType orderType, final OrderStatus current, final OrderStatus target) {
		return switch (orderType) {
			case walk_in -> switch (current) {
				case queued -> target == OrderStatus.processing;
				case processing -> target == OrderStatus.completed;
				default -> false;
			};
			case delivery -> switch (current) {
				case queued -> target == OrderStatus.processing;
				case processing -> target == OrderStatus.transit;
				case transit -> target == OrderStatus.completed;
				default -> false;
			};
		};
	}
}
