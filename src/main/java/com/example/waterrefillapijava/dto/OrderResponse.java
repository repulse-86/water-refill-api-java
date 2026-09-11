package com.example.waterrefillapijava.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.waterrefillapijava.model.DeliveryStatus;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;
import com.example.waterrefillapijava.model.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderResponse(
	Long id,
	@JsonProperty("customer_id")
	Long customerId,
	@JsonProperty("customer_name")
	String customerName,
	@JsonProperty("order_type")
	OrderType orderType,
	OrderStatus status,
	@JsonProperty("payment_method")
	PaymentMethod paymentMethod,
	@JsonProperty("total_amount")
	BigDecimal totalAmount,
	@JsonProperty("amount_paid")
	BigDecimal amountPaid,
	@JsonProperty("change_returned")
	BigDecimal changeReturned,
	@JsonProperty("delivery_fee")
	BigDecimal deliveryFee,
	String notes,
	@JsonProperty("delivery_address")
	String deliveryAddress,
	@JsonProperty("delivery_status")
	DeliveryStatus deliveryStatus,
	@JsonProperty("delivered_at")
	LocalDateTime deliveredAt,
	@JsonProperty("bottles_returned_at_delivery")
	Integer bottlesReturnedAtDelivery,
	@JsonProperty("cash_collected_at_delivery")
	BigDecimal cashCollectedAtDelivery,
	List<OrderItemResponse> items,
	@JsonProperty("created_at")
	LocalDateTime createdAt,
	@JsonProperty("modified_at")
	LocalDateTime modifiedAt
) {
}
