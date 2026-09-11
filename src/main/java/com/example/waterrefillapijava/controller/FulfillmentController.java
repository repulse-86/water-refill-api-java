package com.example.waterrefillapijava.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.waterrefillapijava.dto.FulfillmentBoardResponse;
import com.example.waterrefillapijava.service.OrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/fulfillment")
@RequiredArgsConstructor
public class FulfillmentController {

	private final OrderService orderService;

	@GetMapping("/orders")
	public ResponseEntity<FulfillmentBoardResponse> getBoard() {
		return ResponseEntity.ok(orderService.getBoard());
	}
}
