package com.example.waterrefillapijava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.waterrefillapijava.exception.FieldValidationException;
import com.example.waterrefillapijava.exception.NotFoundException;
import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.DeliveryStatus;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;
import com.example.waterrefillapijava.model.PaymentMethod;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.OrderRepository;
import com.example.waterrefillapijava.service.OrderFulfillmentService;

@ExtendWith(MockitoExtension.class)
class OrderFulfillmentServiceTest {

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private CustomerRepository customerRepository;

	@InjectMocks
	private OrderFulfillmentService fulfillmentService;

	@Test
	void advanceStatusWalkInQueuedToProcessingSucceeds() {
		final Order order = Order.builder()
			.id(1L).orderType(OrderType.walk_in).status(OrderStatus.queued).build();
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
		when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

		final Order result = fulfillmentService.advanceStatus(1L, OrderStatus.processing);

		assertEquals(OrderStatus.processing, result.getStatus());
	}

	@Test
	void advanceStatusWalkInInvalidTransitionThrows() {
		final Order order = Order.builder()
			.id(1L).orderType(OrderType.walk_in).status(OrderStatus.queued).build();
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

		assertThrows(FieldValidationException.class,
			() -> fulfillmentService.advanceStatus(1L, OrderStatus.transit));
	}

	@Test
	void advanceStatusDeliveryCompletedSetsDeliveredAt() {
		final Order order = Order.builder()
			.id(1L).orderType(OrderType.delivery).status(OrderStatus.transit).build();
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
		when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

		final Order result = fulfillmentService.advanceStatus(1L, OrderStatus.completed);

		assertEquals(OrderStatus.completed, result.getStatus());
		assertEquals(DeliveryStatus.delivered, result.getDeliveryStatus());
		assertNotNull(result.getDeliveredAt());
	}

	@Test
	void advanceStatusNotFoundThrows() {
		when(orderRepository.findById(999L)).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class,
			() -> fulfillmentService.advanceStatus(999L, OrderStatus.processing));
	}

	@Test
	void recordDeliveryOnlyForDeliveryOrdersThrows() {
		final Order order = Order.builder()
			.id(1L).orderType(OrderType.walk_in).status(OrderStatus.completed).build();
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

		assertThrows(FieldValidationException.class,
			() -> fulfillmentService.recordDelivery(1L, DeliveryStatus.delivered, 2, BigDecimal.TEN));
	}

	@Test
	void recordDeliveryReducesBottleDebt() {
		final Customer customer = createCustomer(1L, 5);
		final Order order = Order.builder()
			.id(1L).orderType(OrderType.delivery).status(OrderStatus.transit)
			.customer(customer).build();
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
		when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
		when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

		final Order result = fulfillmentService.recordDelivery(1L, DeliveryStatus.delivered, 3, BigDecimal.ZERO);

		assertEquals(2, customer.getBottleDebt());
		assertEquals(3, result.getBottlesReturnedAtDelivery());
		assertEquals(OrderStatus.completed, result.getStatus());
	}

	@Test
	void recordDeliveryReducesOutstandingBalance() {
		final Customer customer = createCustomer(1L, 0);
		customer.setOutstandingBalance(new BigDecimal("500"));
		final Order order = Order.builder()
			.id(1L).orderType(OrderType.delivery).status(OrderStatus.transit)
			.customer(customer).build();
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
		when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
		when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

		final Order result = fulfillmentService.recordDelivery(1L, DeliveryStatus.delivered, 0, new BigDecimal("200"));

		assertEquals(new BigDecimal("300"), customer.getOutstandingBalance());
		assertEquals(new BigDecimal("200"), result.getCashCollectedAtDelivery());
	}

	@Test
	void recordDeliveryNotFoundThrows() {
		when(orderRepository.findById(999L)).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class,
			() -> fulfillmentService.recordDelivery(999L, DeliveryStatus.delivered, 0, BigDecimal.ZERO));
	}

	private Customer createCustomer(final Long id, final int bottleDebt) {
		final Customer c = new Customer();
		c.setId(id);
		c.setBottleDebt(bottleDebt);
		c.setOutstandingBalance(BigDecimal.ZERO);
		return c;
	}
}
