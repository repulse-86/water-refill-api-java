package com.example.waterrefillapijava;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.MeterReading;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderItem;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;
import com.example.waterrefillapijava.model.PaymentMethod;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductType;

import java.time.LocalDate;

class ReportControllerTest extends AbstractIntegrationTest {

	@Test
	void dailySalesReturnsPageResponse() throws Exception {
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

		final Order order = orderRepository.save(Order.builder()
			.customer(customer)
			.orderType(OrderType.delivery)
			.status(OrderStatus.completed)
			.paymentMethod(PaymentMethod.cash)
			.totalAmount(new BigDecimal("100.00"))
			.amountPaid(new BigDecimal("100.00"))
			.build());
		orderItemRepository.save(OrderItem.builder()
			.order(order)
			.product(product)
			.quantity(2)
			.unitPrice(new BigDecimal("50.00"))
			.subtotal(new BigDecimal("100.00"))
			.build());

		mockMvc.perform(get("/api/v1/reports/daily-sales")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.total_items").value(1))
			.andExpect(jsonPath("$.current_page").value(1))
			.andExpect(jsonPath("$.per_page").value(10))
			.andExpect(jsonPath("$.data[0].date").isString())
			.andExpect(jsonPath("$.data[0].order_count").value(1))
			.andExpect(jsonPath("$.data[0].revenue").value(100))
			.andExpect(jsonPath("$.data[0].cash").value(100))
			.andExpect(jsonPath("$.data[0].gallons").value(10));
	}

	@Test
	void productPerformanceReturnsPageResponse() throws Exception {
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

		final Order order = orderRepository.save(Order.builder()
			.customer(customer)
			.orderType(OrderType.delivery)
			.status(OrderStatus.completed)
			.paymentMethod(PaymentMethod.cash)
			.totalAmount(new BigDecimal("100.00"))
			.amountPaid(new BigDecimal("100.00"))
			.build());
		orderItemRepository.save(OrderItem.builder()
			.order(order)
			.product(product)
			.quantity(2)
			.unitPrice(new BigDecimal("50.00"))
			.subtotal(new BigDecimal("100.00"))
			.build());

		mockMvc.perform(get("/api/v1/reports/product-performance")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.total_items").value(1))
			.andExpect(jsonPath("$.data[0].name").value("5 Gallon Refill"))
			.andExpect(jsonPath("$.data[0].type").value("water_refill"))
			.andExpect(jsonPath("$.data[0].units").value(2))
			.andExpect(jsonPath("$.data[0].revenue").value(100))
			.andExpect(jsonPath("$.data[0].share_pct").value(100));
	}

	@Test
	void debtAgingReturnsCustomersWithBalance() throws Exception {
		final String token = loginAsTestUser();

		customerRepository.save(Customer.builder()
			.name("Juan Dela Cruz")
			.phone("09171234567")
			.email("juan@test.com")
			.outstandingBalance(new BigDecimal("500.00"))
			.bottleDebt(3)
			.build());
		customerRepository.save(Customer.builder()
			.name("No Debt")
			.phone("09171234568")
			.email("nodebt@test.com")
			.outstandingBalance(BigDecimal.ZERO)
			.bottleDebt(0)
			.build());

		mockMvc.perform(get("/api/v1/reports/debt-aging")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.total_items").value(1))
			.andExpect(jsonPath("$.data[0].name").value("Juan Dela Cruz"))
			.andExpect(jsonPath("$.data[0].outstanding_balance").value(500))
			.andExpect(jsonPath("$.data[0].bottle_debt").value(3))
			.andExpect(jsonPath("$.data[0].total").value(500));
	}

	@Test
	void reconciliationReturnsPageResponse() throws Exception {
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

		final Order order = orderRepository.save(Order.builder()
			.customer(customer)
			.orderType(OrderType.delivery)
			.status(OrderStatus.completed)
			.paymentMethod(PaymentMethod.cash)
			.totalAmount(new BigDecimal("100.00"))
			.amountPaid(new BigDecimal("100.00"))
			.build());
		orderItemRepository.save(OrderItem.builder()
			.order(order)
			.product(product)
			.quantity(2)
			.unitPrice(new BigDecimal("50.00"))
			.subtotal(new BigDecimal("100.00"))
			.build());

		meterReadingRepository.save(MeterReading.builder()
			.readingDate(LocalDate.of(2026, 8, 31))
			.meterValue(new BigDecimal("1000.00"))
			.build());
		meterReadingRepository.save(MeterReading.builder()
			.readingDate(LocalDate.of(2026, 9, 1))
			.meterValue(new BigDecimal("1010.00"))
			.build());

		mockMvc.perform(get("/api/v1/reports/reconciliation")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.total_items").value(3))
			.andExpect(jsonPath("$.data[0].date").value(LocalDate.now().toString()))
			.andExpect(jsonPath("$.data[0].status").value("No Data"));
	}
}
