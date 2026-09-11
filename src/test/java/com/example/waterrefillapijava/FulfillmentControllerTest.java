package com.example.waterrefillapijava;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderItem;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;
import com.example.waterrefillapijava.model.PaymentMethod;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductType;

class FulfillmentControllerTest extends AbstractIntegrationTest {

	@Test
	void getBoardReturnsOrdersGroupedByStatus() throws Exception {
		final String token = loginAsTestUser();

		final Product product = productRepository.save(Product.builder()
			.name("5 Gallon Refill")
			.type(ProductType.water_refill)
			.volumeGallons(new BigDecimal("5.00"))
			.price(new BigDecimal("50.00"))
			.stockQuantity(100)
			.reorderPoint(10)
			.build());

		final Customer customer = customerRepository.save(Customer.builder()
			.name("Juan Dela Cruz")
			.phone("09171234567")
			.email("juan@test.com")
			.build());

		final Order deliveryOrder = orderRepository.save(Order.builder()
			.customer(customer)
			.orderType(OrderType.delivery)
			.status(OrderStatus.queued)
			.paymentMethod(PaymentMethod.cash)
			.totalAmount(new BigDecimal("100.00"))
			.amountPaid(new BigDecimal("100.00"))
			.deliveryAddress("123 Main St")
			.build());
		orderItemRepository.save(OrderItem.builder()
			.order(deliveryOrder)
			.product(product)
			.quantity(2)
			.unitPrice(new BigDecimal("50.00"))
			.subtotal(new BigDecimal("100.00"))
			.build());

		final Order walkInOrder = orderRepository.save(Order.builder()
			.customer(customer)
			.orderType(OrderType.walk_in)
			.status(OrderStatus.queued)
			.paymentMethod(PaymentMethod.cash)
			.totalAmount(new BigDecimal("50.00"))
			.amountPaid(new BigDecimal("50.00"))
			.build());

		mockMvc.perform(get("/api/v1/fulfillment/orders")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.columns").isNotEmpty())
			.andExpect(jsonPath("$.columns.queued.length()").value(2))
			.andExpect(jsonPath("$.columns.processing.length()").value(0))
			.andExpect(jsonPath("$.columns.transit.length()").value(0))
			.andExpect(jsonPath("$.columns.completed.length()").value(0))
			.andExpect(jsonPath("$.columns.queued[0].customer_name").value("Juan Dela Cruz"))
			.andExpect(jsonPath("$.columns.queued[1].customer_name").value("Juan Dela Cruz"))
			.andExpect(jsonPath("$.columns.queued[0].order_type").value("walk_in"))
			.andExpect(jsonPath("$.columns.queued[1].order_type").value("delivery"));
	}
}