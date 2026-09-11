package com.example.waterrefillapijava.dto;

import java.util.Map;

import com.example.waterrefillapijava.model.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record FulfillmentBoardResponse(
	@JsonProperty("columns")
	Map<OrderStatus, java.util.List<BoardOrderResponse>> columns
) {
}
