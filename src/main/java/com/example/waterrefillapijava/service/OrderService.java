package com.example.waterrefillapijava.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.example.waterrefillapijava.exception.ConflictException;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.dto.OrderItemRequest;
import com.example.waterrefillapijava.dto.OrderItemResponse;
import com.example.waterrefillapijava.dto.OrderResponse;
import com.example.waterrefillapijava.exception.FieldValidationException;
import com.example.waterrefillapijava.exception.NotFoundException;
import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.DeliveryStatus;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderItem;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;
import com.example.waterrefillapijava.model.PaymentMethod;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.OrderItemRepository;
import com.example.waterrefillapijava.repository.OrderRepository;
import com.example.waterrefillapijava.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;
	private final CustomerRepository customerRepository;
	private final StockEffectService stockEffectService;

	@Transactional(readOnly = true)
	public Page<Order> listAll(final Pageable pageable) {
		return orderRepository.findByDeletedFalse(pageable);
	}

	@Transactional(readOnly = true)
	public Page<Order> search(final String search, final OrderType orderType, final OrderStatus status,
			final Pageable pageable) {
		if (search != null && orderType != null && status != null) {
			return orderRepository.findByOrderTypeAndStatusAndSearchAndDeletedFalse(orderType, status, search, pageable);
		}
		if (search != null && orderType != null) {
			return orderRepository.findByOrderTypeAndSearchAndDeletedFalse(orderType, search, pageable);
		}
		if (search != null && status != null) {
			return orderRepository.findByStatusAndSearchAndDeletedFalse(status, search, pageable);
		}
		if (orderType != null && status != null) {
			return orderRepository.findByOrderTypeAndStatusAndDeletedFalse(orderType, status, pageable);
		}
		if (search != null) {
			return orderRepository.findBySearchAndDeletedFalse(search, pageable);
		}
		if (orderType != null) {
			return orderRepository.findByOrderTypeAndDeletedFalse(orderType, pageable);
		}
		if (status != null) {
			return orderRepository.findByStatusAndDeletedFalse(status, pageable);
		}
		return orderRepository.findByDeletedFalse(pageable);
	}

	@Transactional(readOnly = true)
	public Order findById(final Long id) {
		final Order order = orderRepository.findByIdWithDetails(id);
		if (order == null || order.isDeleted()) {
			throw new NotFoundException("Order not found.");
		}
		return order;
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict(value = "report:daily-sales", allEntries = true),
		@CacheEvict(value = "report:product-performance", allEntries = true),
		@CacheEvict(value = "report:debt-aging", allEntries = true),
		@CacheEvict(value = "report:reconciliation", allEntries = true)
	})
	public Order create(final Long customerId, final OrderType orderType, final PaymentMethod paymentMethod,
			final BigDecimal totalAmount, final BigDecimal amountPaid, final BigDecimal deliveryFee,
			final String notes, final String deliveryAddress, final Integer bottlesReturned,
			final List<OrderItemRequest> items) {

		Customer customer = null;
		if (customerId != null) {
			customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new NotFoundException("Customer not found."));
		}

		if (orderType == OrderType.delivery && (deliveryAddress == null || deliveryAddress.isBlank())) {
			throw FieldValidationException.builder()
				.add("delivery_address", "The delivery address field is required for delivery orders.")
				.build();
		}

		BigDecimal computedDeliveryFee = deliveryFee != null ? deliveryFee : BigDecimal.ZERO;
		BigDecimal computedAmountPaid = amountPaid != null ? amountPaid : BigDecimal.ZERO;

		final Order order = Order.builder()
			.customer(customer)
			.orderType(orderType)
			.status(OrderStatus.queued)
			.paymentMethod(paymentMethod)
			.totalAmount(BigDecimal.ZERO)
			.amountPaid(computedAmountPaid)
			.changeReturned(BigDecimal.ZERO)
			.deliveryFee(computedDeliveryFee)
			.notes(notes)
			.deliveryAddress(orderType == OrderType.delivery ? deliveryAddress : null)
			.deliveryStatus(orderType == OrderType.delivery ? DeliveryStatus.pending : null)
			.bottlesReturnedAtDelivery(bottlesReturned != null ? bottlesReturned : 0)
			.cashCollectedAtDelivery(BigDecimal.ZERO)
			.build();

		orderRepository.save(order);

		BigDecimal computedTotal = BigDecimal.ZERO;
		final List<Long> productIds = items.stream()
			.map(OrderItemRequest::productId)
			.toList();
		final Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
			.collect(Collectors.toMap(Product::getId, p -> p));

		for (OrderItemRequest item : items) {
			final Product product = productsById.get(item.productId());
			if (product == null) {
				throw new NotFoundException("Product not found.");
			}

			if (product.getStockQuantity() < item.quantity()) {
				throw FieldValidationException.builder()
					.add("items", "Insufficient stock for product '" + product.getName() + "'.")
					.build();
			}

			final BigDecimal unitPrice = product.getPrice();

			final OrderItem orderItem = OrderItem.builder()
				.order(order)
				.product(product)
				.quantity(item.quantity())
				.unitPrice(unitPrice)
				.subtotal(unitPrice.multiply(BigDecimal.valueOf(item.quantity())))
				.build();

			orderItemRepository.save(orderItem);
			order.getItems().add(orderItem);
			computedTotal = computedTotal.add(orderItem.getSubtotal());

			stockEffectService.applyProductStockDeduction(product, item.quantity());
		}

		order.setTotalAmount(computedTotal.add(computedDeliveryFee));
		order.setChangeReturned(order.getAmountPaid().subtract(order.getTotalAmount()).max(BigDecimal.ZERO));
		orderRepository.save(order);

		stockEffectService.applySaleEffects(order);

		return order;
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict(value = "report:daily-sales", allEntries = true),
		@CacheEvict(value = "report:product-performance", allEntries = true),
		@CacheEvict(value = "report:debt-aging", allEntries = true),
		@CacheEvict(value = "report:reconciliation", allEntries = true)
	})
	public Order update(final Long id, final Long customerId, final OrderType orderType,
			final OrderStatus status, final PaymentMethod paymentMethod, final BigDecimal totalAmount,
			final BigDecimal amountPaid, final BigDecimal deliveryFee, final String notes,
			final String deliveryAddress, final DeliveryStatus deliveryStatus,
			final List<OrderItemRequest> items) {

		final Order order = findById(id);

		Customer customer = null;
		if (customerId != null) {
			customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new NotFoundException("Customer not found."));
		}

		order.setCustomer(customer);
		order.setOrderType(orderType);
		order.setStatus(status);
		order.setPaymentMethod(paymentMethod);
		order.setTotalAmount(totalAmount);
		order.setAmountPaid(amountPaid);
		order.setChangeReturned(amountPaid.subtract(totalAmount).max(BigDecimal.ZERO));
		order.setDeliveryFee(deliveryFee != null ? deliveryFee : BigDecimal.ZERO);
		order.setNotes(notes);
		order.setDeliveryAddress(deliveryAddress);
		order.setDeliveryStatus(deliveryStatus);

		orderItemRepository.findByOrderId(id).forEach(orderItemRepository::delete);
		order.getItems().clear();

		final List<Long> productIds = items.stream()
			.map(OrderItemRequest::productId)
			.toList();
		final Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
			.collect(Collectors.toMap(Product::getId, p -> p));

		BigDecimal computedTotal = BigDecimal.ZERO;
		for (OrderItemRequest item : items) {
			final Product product = productsById.get(item.productId());
			if (product == null) {
				throw new NotFoundException("Product not found.");
			}

			final BigDecimal unitPrice = product.getPrice();

			final OrderItem orderItem = OrderItem.builder()
				.order(order)
				.product(product)
				.quantity(item.quantity())
				.unitPrice(unitPrice)
				.subtotal(unitPrice.multiply(BigDecimal.valueOf(item.quantity())))
				.build();

			orderItemRepository.save(orderItem);
			order.getItems().add(orderItem);
			computedTotal = computedTotal.add(orderItem.getSubtotal());
		}

		order.setTotalAmount(computedTotal.add(order.getDeliveryFee()));
		order.setChangeReturned(order.getAmountPaid().subtract(order.getTotalAmount()).max(BigDecimal.ZERO));

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
	public void delete(final Long id) {
		final Order order = orderRepository.findByIdWithDetails(id);
		if (order == null) {
			throw new NotFoundException("Order not found.");
		}
		if (order.isDeleted()) {
			return;
		}
		order.setDeleted(true);
		order.setDeletedAt(LocalDateTime.now());
		orderRepository.save(order);
	}

	public OrderResponse toResponse(final Order order) {
		final List<OrderItemResponse> itemResponses = order.getItems().stream()
			.map(this::toItemResponse)
			.toList();

		return new OrderResponse(
			order.getId(),
			order.getCustomer() != null ? order.getCustomer().getId() : null,
			order.getCustomer() != null ? order.getCustomer().getName() : "Walk-in",
			order.getOrderType(),
			order.getStatus(),
			order.getPaymentMethod(),
			order.getTotalAmount(),
			order.getAmountPaid(),
			order.getChangeReturned(),
			order.getDeliveryFee(),
			order.getNotes(),
			order.getDeliveryAddress(),
			order.getDeliveryStatus(),
			order.getDeliveredAt(),
			order.getBottlesReturnedAtDelivery(),
			order.getCashCollectedAtDelivery(),
			itemResponses,
			order.getCreatedAt(),
			order.getModifiedAt(),
			order.getDeletedAt() != null ? order.getDeletedAt().toString() : null
		);
	}

	@Transactional(readOnly = true)
	public List<OrderResponse> toResponses(final List<Order> orders) {
		if (orders.isEmpty()) {
			return List.of();
		}

		final List<Long> orderIds = orders.stream().map(Order::getId).toList();

		final Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository.findByOrderIdInWithProduct(orderIds).stream()
			.collect(Collectors.groupingBy(oi -> oi.getOrder().getId()));

		final Set<Long> customerIds = orders.stream()
			.map(o -> o.getCustomer() != null ? o.getCustomer().getId() : null)
			.filter(id -> id != null)
			.collect(Collectors.toSet());
		final Map<Long, Customer> customersById = customerIds.isEmpty()
			? Map.of()
			: customerRepository.findAllById(customerIds).stream()
				.collect(Collectors.toMap(Customer::getId, c -> c));

		return orders.stream()
			.map(o -> {
				final List<OrderItem> items = itemsByOrderId.getOrDefault(o.getId(), List.of());
				final List<OrderItemResponse> itemResponses = items.stream()
					.map(this::toItemResponse)
					.toList();

				final Long custId = o.getCustomer() != null ? o.getCustomer().getId() : null;
				final Customer customer = custId != null ? customersById.get(custId) : null;

				return new OrderResponse(
					o.getId(),
					customer != null ? customer.getId() : null,
					customer != null ? customer.getName() : "Walk-in",
					o.getOrderType(),
					o.getStatus(),
					o.getPaymentMethod(),
					o.getTotalAmount(),
					o.getAmountPaid(),
					o.getChangeReturned(),
					o.getDeliveryFee(),
					o.getNotes(),
					o.getDeliveryAddress(),
					o.getDeliveryStatus(),
					o.getDeliveredAt(),
					o.getBottlesReturnedAtDelivery(),
					o.getCashCollectedAtDelivery(),
					itemResponses,
					o.getCreatedAt(),
					o.getModifiedAt(),
					o.getDeletedAt() != null ? o.getDeletedAt().toString() : null
				);
			})
			.toList();
	}

	@Transactional(readOnly = true)
	public Page<Order> archiveList(String search, OrderType orderType, OrderStatus status, Pageable pageable) {
		if (search != null && orderType != null && status != null) {
			return orderRepository.findByOrderTypeAndStatusAndSearchAndDeletedTrue(orderType, status, search, pageable);
		}
		if (search != null && orderType != null) {
			return orderRepository.findByOrderTypeAndSearchAndDeletedTrue(orderType, search, pageable);
		}
		if (search != null && status != null) {
			return orderRepository.findByStatusAndSearchAndDeletedTrue(status, search, pageable);
		}
		if (orderType != null && status != null) {
			return orderRepository.findByOrderTypeAndStatusAndDeletedTrue(orderType, status, pageable);
		}
		if (search != null) {
			return orderRepository.findBySearchAndDeletedTrue(search, pageable);
		}
		if (orderType != null) {
			return orderRepository.findByOrderTypeAndDeletedTrue(orderType, pageable);
		}
		if (status != null) {
			return orderRepository.findByStatusAndDeletedTrue(status, pageable);
		}
		return orderRepository.findByDeletedTrue(pageable);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict(value = "report:daily-sales", allEntries = true),
		@CacheEvict(value = "report:product-performance", allEntries = true),
		@CacheEvict(value = "report:debt-aging", allEntries = true),
		@CacheEvict(value = "report:reconciliation", allEntries = true)
	})
	public void restore(final Long id) {
		final Order order = orderRepository.findByIdWithDetails(id);
		if (order == null || !order.isDeleted()) {
			throw new ConflictException("Order is not archived.");
		}
		order.setDeleted(false);
		order.setDeletedAt(null);
		orderRepository.save(order);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict(value = "report:daily-sales", allEntries = true),
		@CacheEvict(value = "report:product-performance", allEntries = true),
		@CacheEvict(value = "report:debt-aging", allEntries = true),
		@CacheEvict(value = "report:reconciliation", allEntries = true)
	})
	public void permanentDelete(final Long id) {
		final Order order = orderRepository.findByIdWithDetails(id);
		if (order == null || !order.isDeleted()) {
			throw new com.example.waterrefillapijava.exception.ConflictException("Order must be archived before permanent deletion.");
		}

		stockEffectService.restoreStockForOrder(order);
		orderItemRepository.findByOrderId(id).forEach(orderItemRepository::delete);
		orderRepository.deleteById(id);
	}

	private OrderItemResponse toItemResponse(final OrderItem item) {
		return new OrderItemResponse(
			item.getId(),
			item.getProduct().getId(),
			item.getProduct().getName(),
			item.getQuantity(),
			item.getUnitPrice(),
			item.getSubtotal()
		);
	}
}
