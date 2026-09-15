package com.example.waterrefillapijava.order.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.waterrefillapijava.order.dto.FulfillmentBoardResponse;
import com.example.waterrefillapijava.order.service.OrderFulfillmentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/fulfillment")
@RequiredArgsConstructor
public class FulfillmentController {

	private final OrderFulfillmentService orderFulfillmentService;

	@GetMapping("/orders")
	public ResponseEntity<FulfillmentBoardResponse> getBoard() {
		return ResponseEntity.ok(orderFulfillmentService.getBoard());
	}
}
