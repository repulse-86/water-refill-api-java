package com.example.waterrefillapijava.order.dto;

import java.util.Map;

import com.example.waterrefillapijava.order.model.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record FulfillmentBoardResponse(
	@JsonProperty("columns")
	Map<OrderStatus, java.util.List<BoardOrderResponse>> columns
) {
}
