package com.example.waterrefillapijava.dashboard.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.waterrefillapijava.dashboard.dto.DashboardResponse;
import com.example.waterrefillapijava.dashboard.service.DashboardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

	private final DashboardService dashboardService;

	@GetMapping
	public ResponseEntity<DashboardResponse> getDashboard() {
		return ResponseEntity.ok(dashboardService.getDashboard());
	}
}
