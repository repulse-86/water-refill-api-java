package com.example.waterrefillapijava.order.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.waterrefillapijava.shared.exception.FieldValidationException;
import com.example.waterrefillapijava.customer.model.Customer;
import com.example.waterrefillapijava.order.model.Order;
import com.example.waterrefillapijava.order.model.OrderItem;
import com.example.waterrefillapijava.order.model.PaymentMethod;
import com.example.waterrefillapijava.product.model.Product;
import com.example.waterrefillapijava.product.model.ProductComponent;
import com.example.waterrefillapijava.product.model.ProductType;
import com.example.waterrefillapijava.customer.repository.CustomerRepository;
import com.example.waterrefillapijava.order.repository.OrderItemRepository;
import com.example.waterrefillapijava.product.repository.ProductComponentRepository;
import com.example.waterrefillapijava.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockEffectService {

	private final ProductRepository productRepository;
	private final ProductComponentRepository productComponentRepository;
	private final CustomerRepository customerRepository;
	private final OrderItemRepository orderItemRepository;

	public void applyProductStockDeduction(final Product product, final int quantity) {
		final int updated = productRepository.decrementStockIfAvailable(product.getId(), quantity);
		if (updated == 0) {
			throw FieldValidationException.builder()
				.add("items", "Insufficient stock for product '" + product.getName() + "'.")
				.build();
		}

		if (product.getType() == ProductType.water_refill) {
			applyBomConsumption(product.getId(), quantity);
		}
	}

	public void restoreStockForOrder(final Order order) {
		final List<OrderItem> items = orderItemRepository.findByOrderIdWithProduct(order.getId());
		for (OrderItem item : items) {
			productRepository.incrementStock(item.getProduct().getId(), item.getQuantity());
		}

		final List<Long> waterRefillProductIds = items.stream()
			.filter(item -> item.getProduct().getType() == ProductType.water_refill)
			.map(item -> item.getProduct().getId())
			.distinct()
			.toList();

		if (!waterRefillProductIds.isEmpty()) {
			final List<ProductComponent> allComponents = productComponentRepository
				.findByProductIdInJoinFetchComponent(waterRefillProductIds);
			final Map<Long, List<ProductComponent>> componentsByProductId = allComponents.stream()
				.collect(Collectors.groupingBy(pc -> pc.getProduct().getId()));

			for (OrderItem item : items) {
				if (item.getProduct().getType() == ProductType.water_refill) {
					final List<ProductComponent> components = componentsByProductId
						.getOrDefault(item.getProduct().getId(), List.of());
					for (ProductComponent pc : components) {
						final int restoreQty = pc.getQuantity() * item.getQuantity();
						productRepository.incrementStock(pc.getComponent().getId(), restoreQty);
					}
				}
			}
		}

		if (order.getPaymentMethod() == PaymentMethod.credit && order.getCustomer() != null) {
			customerRepository.adjustBalance(order.getCustomer().getId(), order.getTotalAmount().negate());
		}
	}

	public void applySaleEffects(final Order order) {
		if (order.getPaymentMethod() == PaymentMethod.credit && order.getCustomer() != null) {
			customerRepository.adjustBalance(order.getCustomer().getId(), order.getTotalAmount());
		}

		if (order.getBottlesReturnedAtDelivery() != null && order.getBottlesReturnedAtDelivery() > 0
				&& order.getCustomer() != null) {
			customerRepository.decrementBottleDebt(order.getCustomer().getId(), order.getBottlesReturnedAtDelivery());
		}
	}

	private void applyBomConsumption(final Long productId, final int quantity) {
		final List<ProductComponent> components = productComponentRepository
			.findByProductIdJoinFetchComponent(productId);

		for (ProductComponent pc : components) {
			final int consumeQty = pc.getQuantity() * quantity;
			final int updated = productRepository.decrementStockIfAvailable(pc.getComponent().getId(), consumeQty);
			if (updated == 0) {
				throw FieldValidationException.builder()
					.add("items", "Insufficient stock for component product '" + pc.getComponent().getName() + "'.")
					.build();
			}
		}
	}
}
