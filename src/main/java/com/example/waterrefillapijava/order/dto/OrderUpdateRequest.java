package com.example.waterrefillapijava.order.dto;

import java.math.BigDecimal;
import java.util.List;

import com.example.waterrefillapijava.order.model.DeliveryStatus;
import com.example.waterrefillapijava.order.model.OrderStatus;
import com.example.waterrefillapijava.order.model.OrderType;
import com.example.waterrefillapijava.order.model.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record OrderUpdateRequest(
	@JsonProperty("customer_id")
	Long customerId,

	@NotNull(message = "The order type field is required.")
	@JsonProperty("order_type")
	OrderType orderType,

	@NotNull(message = "The status field is required.")
	OrderStatus status,

	@NotNull(message = "The payment method field is required.")
	@JsonProperty("payment_method")
	PaymentMethod paymentMethod,

	@JsonProperty("total_amount")
	BigDecimal totalAmount,

	@JsonProperty("amount_paid")
	BigDecimal amountPaid,

	@JsonProperty("delivery_fee")
	BigDecimal deliveryFee,

	String notes,

	@JsonProperty("delivery_address")
	String deliveryAddress,

	@JsonProperty("delivery_status")
	DeliveryStatus deliveryStatus,

	@NotEmpty(message = "The items field must not be empty.")
	@Valid
	List<OrderItemRequest> items
) {
}
