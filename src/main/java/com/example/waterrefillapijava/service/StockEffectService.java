package com.example.waterrefillapijava.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderItem;
import com.example.waterrefillapijava.model.PaymentMethod;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductComponent;
import com.example.waterrefillapijava.model.ProductType;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.OrderItemRepository;
import com.example.waterrefillapijava.repository.ProductComponentRepository;
import com.example.waterrefillapijava.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockEffectService {

	private final ProductRepository productRepository;
	private final ProductComponentRepository productComponentRepository;
	private final CustomerRepository customerRepository;
	private final OrderItemRepository orderItemRepository;

	public void applyProductStockDeduction(final Product product, final int quantity) {
		product.setStockQuantity(product.getStockQuantity() - quantity);
		productRepository.save(product);

		if (product.getType() == ProductType.water_refill) {
			applyBomConsumption(product, quantity);
		}
	}

	public void restoreStockForOrder(final Order order) {
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

	public void applySaleEffects(final Order order) {
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

	private void applyBomConsumption(final Product product, final int quantity) {
		final List<ProductComponent> components = productComponentRepository
			.findByProductId(product.getId(), Pageable.unpaged()).getContent();

		for (ProductComponent pc : components) {
			final Product componentProduct = pc.getComponent();
			final int consumeQty = pc.getQuantity() * quantity;
			componentProduct.setStockQuantity(componentProduct.getStockQuantity() - consumeQty);
			productRepository.save(componentProduct);
		}
	}
}
