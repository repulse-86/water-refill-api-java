package com.example.waterrefillapijava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.waterrefillapijava.dto.OrderItemRequest;
import com.example.waterrefillapijava.exception.FieldValidationException;
import com.example.waterrefillapijava.exception.NotFoundException;
import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;
import com.example.waterrefillapijava.model.PaymentMethod;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.OrderItemRepository;
import com.example.waterrefillapijava.repository.OrderRepository;
import com.example.waterrefillapijava.repository.ProductRepository;
import com.example.waterrefillapijava.service.OrderService;
import com.example.waterrefillapijava.service.StockEffectService;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderItemRepository orderItemRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private CustomerRepository customerRepository;

	@Mock
	private StockEffectService stockEffectService;

	@InjectMocks
	private OrderService orderService;

	private Product createProduct(final Long id, final String name, final BigDecimal price, final int stock) {
		final Product p = new Product();
		p.setId(id);
		p.setName(name);
		p.setPrice(price);
		p.setStockQuantity(stock);
		return p;
	}

	private Customer createCustomer(final Long id, final String name, final int bottleDebt) {
		final Customer c = new Customer();
		c.setId(id);
		c.setName(name);
		c.setBottleDebt(bottleDebt);
		c.setOutstandingBalance(BigDecimal.ZERO);
		return c;
	}

	@Test
	void findByIdNotFoundThrows() {
		when(orderRepository.findById(999L)).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, () -> orderService.findById(999L));
	}

	@Test
	void findByIdReturnsOrder() {
		final Order order = Order.builder().id(1L).orderType(OrderType.walk_in).build();
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

		final Order result = orderService.findById(1L);

		assertEquals(1L, result.getId());
	}

	@Test
	void createWalkInOrderComputesTotalAndDeductsStock() {
		final Product product = createProduct(10L, "Water", new BigDecimal("25"), 50);
		when(customerRepository.findById(1L)).thenReturn(Optional.of(createCustomer(1L, "Juan", 0)));
		when(productRepository.findById(10L)).thenReturn(Optional.of(product));
		when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
			final Order o = inv.getArgument(0);
			o.setId(1L);
			return o;
		});

		final OrderItemRequest item = new OrderItemRequest(10L, 3, null);

		final Order result = orderService.create(
			1L, OrderType.walk_in, PaymentMethod.cash,
			null, null, BigDecimal.ZERO,
			null, null, 0, List.of(item)
		);

		assertNotNull(result);
		assertEquals(new BigDecimal("75"), result.getTotalAmount());
		verify(stockEffectService).applyProductStockDeduction(product, 3);
	}

	@Test
	void createDeliveryOrderRequiresAddress() {
		final FieldValidationException ex = assertThrows(FieldValidationException.class,
			() -> orderService.create(
				null, OrderType.delivery, PaymentMethod.cash,
				null, null, BigDecimal.ZERO,
				null, null, 0, List.of()
			));
		assertNotNull(ex);
	}

	@Test
	void createOrderWithInsufficientStockThrows() {
		final Product product = createProduct(10L, "Water", new BigDecimal("25"), 2);
		when(productRepository.findById(10L)).thenReturn(Optional.of(product));

		final OrderItemRequest item = new OrderItemRequest(10L, 5, null);

		assertThrows(FieldValidationException.class,
			() -> orderService.create(
				null, OrderType.walk_in, PaymentMethod.cash,
				null, null, BigDecimal.ZERO,
				null, null, 0, List.of(item)
			));
	}

	@Test
	void createCreditOrderAppliesSaleEffects() {
		final Customer customer = createCustomer(1L, "Juan", 0);
		final Product product = createProduct(10L, "Water", new BigDecimal("25"), 50);
		when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
		when(productRepository.findById(10L)).thenReturn(Optional.of(product));
		when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
			final Order o = inv.getArgument(0);
			o.setId(1L);
			return o;
		});

		final OrderItemRequest item = new OrderItemRequest(10L, 2, null);

		final Order result = orderService.create(
			1L, OrderType.walk_in, PaymentMethod.credit,
			null, null, BigDecimal.ZERO,
			null, null, 0, List.of(item)
		);

		assertNotNull(result);
		verify(stockEffectService).applySaleEffects(any(Order.class));
	}

	@Test
	void deleteRestoresStockAndDeletesItems() {
		final Order order = Order.builder()
			.id(1L)
			.orderType(OrderType.walk_in)
			.paymentMethod(PaymentMethod.cash)
			.totalAmount(new BigDecimal("25"))
			.customer(null)
			.build();
		when(orderRepository.existsById(1L)).thenReturn(true);
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

		orderService.delete(1L);

		verify(stockEffectService).restoreStockForOrder(order);
		verify(orderItemRepository).findByOrderId(1L);
		verify(orderRepository).deleteById(1L);
	}

	@Test
	void deleteNotFoundThrows() {
		when(orderRepository.existsById(999L)).thenReturn(false);

		assertThrows(NotFoundException.class, () -> orderService.delete(999L));
	}

	@Test
	void toResponseMapsFieldsCorrectly() {
		final Customer customer = createCustomer(1L, "Juan", 0);
		final Product product = createProduct(10L, "Water", new BigDecimal("25"), 50);

		final com.example.waterrefillapijava.model.OrderItem item =
			new com.example.waterrefillapijava.model.OrderItem();
		item.setId(1L);
		item.setProduct(product);
		item.setQuantity(3);
		item.setUnitPrice(new BigDecimal("25"));
		item.setSubtotal(new BigDecimal("75"));

		final Order order = Order.builder()
			.id(1L)
			.customer(customer)
			.orderType(OrderType.walk_in)
			.status(OrderStatus.queued)
			.paymentMethod(PaymentMethod.cash)
			.totalAmount(new BigDecimal("75"))
			.amountPaid(new BigDecimal("100"))
			.changeReturned(new BigDecimal("25"))
			.deliveryFee(BigDecimal.ZERO)
			.items(new java.util.ArrayList<>(List.of(item)))
			.bottlesReturnedAtDelivery(0)
			.cashCollectedAtDelivery(BigDecimal.ZERO)
			.build();

		final var response = orderService.toResponse(order);

		assertEquals(1L, response.id());
		assertEquals("Juan", response.customerName());
		assertEquals(OrderType.walk_in, response.orderType());
		assertEquals(1, response.items().size());
		assertEquals("Water", response.items().get(0).productName());
	}
}
