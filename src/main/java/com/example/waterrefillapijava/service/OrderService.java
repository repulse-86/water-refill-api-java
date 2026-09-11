package com.example.waterrefillapijava.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.dto.BoardOrderResponse;
import com.example.waterrefillapijava.dto.FulfillmentBoardResponse;
import com.example.waterrefillapijava.dto.OrderItemRequest;
import com.example.waterrefillapijava.dto.OrderItemResponse;
import com.example.waterrefillapijava.dto.OrderResponse;
import com.example.waterrefillapijava.exception.ConflictException;
import com.example.waterrefillapijava.exception.FieldValidationException;
import com.example.waterrefillapijava.exception.NotFoundException;
import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.DeliveryStatus;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderItem;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;
import com.example.waterrefillapijava.model.PaymentMethod;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductComponent;
import com.example.waterrefillapijava.model.ProductType;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.OrderItemRepository;
import com.example.waterrefillapijava.repository.OrderRepository;
import com.example.waterrefillapijava.repository.ProductComponentRepository;
import com.example.waterrefillapijava.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;
	private final CustomerRepository customerRepository;
	private final ProductComponentRepository productComponentRepository;

	private static final Map<OrderType, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
		OrderType.walk_in, Set.of(OrderStatus.queued, OrderStatus.processing, OrderStatus.completed),
		OrderType.delivery, Set.of(OrderStatus.queued, OrderStatus.processing, OrderStatus.transit, OrderStatus.completed)
	);

	@Transactional(readOnly = true)
	public Page<Order> listAll(Pageable pageable) {
		return orderRepository.findAll(pageable);
	}

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

	@Transactional(readOnly = true)
	public Page<Order> search(String search, OrderType orderType, OrderStatus status, Pageable pageable) {
		if (search != null && orderType != null && status != null) {
			return orderRepository.findByOrderTypeAndStatusAndSearch(orderType, status, search, pageable);
		}
		if (search != null && orderType != null) {
			return orderRepository.findByOrderTypeAndSearch(orderType, search, pageable);
		}
		if (search != null && status != null) {
			return orderRepository.findByStatusAndSearch(status, search, pageable);
		}
		if (orderType != null && status != null) {
			return orderRepository.findByOrderTypeAndStatus(orderType, status, pageable);
		}
		if (search != null) {
			return orderRepository.findBySearch(search, pageable);
		}
		if (orderType != null) {
			return orderRepository.findByOrderType(orderType, pageable);
		}
		if (status != null) {
			return orderRepository.findByStatus(status, pageable);
		}
		return orderRepository.findAll(pageable);
	}

	@Transactional(readOnly = true)
	public Order findById(Long id) {
		return orderRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Order not found."));
	}

	@Transactional
	public Order create(Long customerId, OrderType orderType, PaymentMethod paymentMethod,
			BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal deliveryFee,
			String notes, String deliveryAddress, Integer bottlesReturned,
			List<OrderItemRequest> items) {

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

		if (totalAmount == null) {
			totalAmount = items.stream()
				.map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		}

		if (amountPaid == null) {
			amountPaid = totalAmount;
		}

		final BigDecimal changeReturned = amountPaid.subtract(totalAmount).max(BigDecimal.ZERO);

		if (deliveryFee == null) {
			deliveryFee = BigDecimal.ZERO;
		}

		final Order order = Order.builder()
			.customer(customer)
			.orderType(orderType)
			.status(OrderStatus.queued)
			.paymentMethod(paymentMethod)
			.totalAmount(totalAmount.add(deliveryFee))
			.amountPaid(amountPaid)
			.changeReturned(changeReturned)
			.deliveryFee(deliveryFee)
			.notes(notes)
			.deliveryAddress(orderType == OrderType.delivery ? deliveryAddress : null)
			.deliveryStatus(orderType == OrderType.delivery ? DeliveryStatus.pending : null)
			.bottlesReturnedAtDelivery(bottlesReturned != null ? bottlesReturned : 0)
			.cashCollectedAtDelivery(BigDecimal.ZERO)
			.build();

		orderRepository.save(order);

		BigDecimal computedTotal = BigDecimal.ZERO;
		for (OrderItemRequest item : items) {
			final Product product = productRepository.findById(item.productId())
				.orElseThrow(() -> new NotFoundException("Product not found."));

			if (product.getStockQuantity() < item.quantity()) {
				throw FieldValidationException.builder()
					.add("items", "Insufficient stock for product '" + product.getName() + "'.")
					.build();
			}

			final BigDecimal unitPrice = item.unitPrice() != null ? item.unitPrice() : product.getPrice();

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

			applyProductStockDeduction(product, item.quantity());
		}

		order.setTotalAmount(computedTotal.add(deliveryFee));
		if (amountPaid.compareTo(order.getTotalAmount()) < 0 && paymentMethod != PaymentMethod.credit) {
			// allow partial payment — amount_paid stays as-is
		}
		order.setChangeReturned(order.getAmountPaid().subtract(order.getTotalAmount()).max(BigDecimal.ZERO));
		orderRepository.save(order);

		applySaleEffects(order, items);

		return order;
	}

	@Transactional
	public Order update(Long id, Long customerId, OrderType orderType, OrderStatus status,
			PaymentMethod paymentMethod, BigDecimal totalAmount, BigDecimal amountPaid,
			BigDecimal deliveryFee, String notes, String deliveryAddress,
			DeliveryStatus deliveryStatus, List<OrderItemRequest> items) {

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

		BigDecimal computedTotal = BigDecimal.ZERO;
		for (OrderItemRequest item : items) {
			final Product product = productRepository.findById(item.productId())
				.orElseThrow(() -> new NotFoundException("Product not found."));

			final BigDecimal unitPrice = item.unitPrice() != null ? item.unitPrice() : product.getPrice();

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
	public void delete(Long id) {
		if (!orderRepository.existsById(id)) {
			throw new NotFoundException("Order not found.");
		}

		final Order order = orderRepository.findById(id).orElseThrow();
		restoreStockForOrder(order);

		orderItemRepository.findByOrderId(id).forEach(orderItemRepository::delete);
		orderRepository.deleteById(id);
	}

	@Transactional
	public Order advanceStatus(Long id, OrderStatus newStatus) {
		final Order order = findById(id);

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
	public Order recordDelivery(Long id, DeliveryStatus deliveryStatus, Integer bottlesReturned, Double cashCollected) {
		final Order order = findById(id);

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

		if (cashCollected != null && cashCollected > 0 && order.getCustomer() != null) {
			final Customer customer = order.getCustomer();
			customer.setOutstandingBalance(customer.getOutstandingBalance().subtract(BigDecimal.valueOf(cashCollected)));
			customerRepository.save(customer);
			order.setCashCollectedAtDelivery(BigDecimal.valueOf(cashCollected));
		}

		if (deliveryStatus == DeliveryStatus.delivered) {
			order.setStatus(OrderStatus.completed);
		}

		return orderRepository.save(order);
	}

	private void applyProductStockDeduction(Product product, int quantity) {
		product.setStockQuantity(product.getStockQuantity() - quantity);
		productRepository.save(product);

		if (product.getType() == ProductType.water_refill) {
			applyBomConsumption(product, quantity);
		}
	}

	private void applyBomConsumption(Product product, int quantity) {
		final List<ProductComponent> components = productComponentRepository
			.findByProductId(product.getId(), Pageable.unpaged()).getContent();

		for (ProductComponent pc : components) {
			final Product componentProduct = pc.getComponent();
			final int consumeQty = pc.getQuantity() * quantity;
			componentProduct.setStockQuantity(componentProduct.getStockQuantity() - consumeQty);
			productRepository.save(componentProduct);
		}
	}

	private void applySaleEffects(Order order, List<OrderItemRequest> items) {
		if (order.getPaymentMethod() == PaymentMethod.credit && order.getCustomer() != null) {
			final Customer customer = order.getCustomer();
			customer.setOutstandingBalance(customer.getOutstandingBalance().add(order.getTotalAmount()));
			customerRepository.save(customer);
		}

		if (order.getBottlesReturnedAtDelivery() != null && order.getBottlesReturnedAtDelivery() > 0
				&& order.getCustomer() != null) {
			final Customer customer = order.getCustomer();
			customer.setBottleDebt(Math.max(0, customer.getBottleDebt() - order.getBottlesReturnedAtDelivery()));
			customerRepository.save(customer);
		}
	}

	private void restoreStockForOrder(Order order) {
		final List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
		for (OrderItem item : items) {
			final Product product = item.getProduct();
			product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
			productRepository.save(product);

			if (product.getType() == ProductType.water_refill) {
				final List<ProductComponent> components = productComponentRepository
					.findByProductId(product.getId(), Pageable.unpaged()).getContent();
				for (ProductComponent pc : components) {
					final Product componentProduct = pc.getComponent();
					final int restoreQty = pc.getQuantity() * item.getQuantity();
					componentProduct.setStockQuantity(componentProduct.getStockQuantity() + restoreQty);
					productRepository.save(componentProduct);
				}
			}
		}

		if (order.getPaymentMethod() == PaymentMethod.credit && order.getCustomer() != null) {
			final Customer customer = order.getCustomer();
			customer.setOutstandingBalance(customer.getOutstandingBalance().subtract(order.getTotalAmount()));
			customerRepository.save(customer);
		}
	}

	private boolean isValidTransition(OrderType orderType, OrderStatus current, OrderStatus target) {
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

	public OrderResponse toResponse(Order order) {
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
			order.getModifiedAt()
		);
	}

	private OrderItemResponse toItemResponse(OrderItem item) {
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
