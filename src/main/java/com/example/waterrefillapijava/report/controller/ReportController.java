package com.example.waterrefillapijava.report.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.waterrefillapijava.report.dto.DailySalesRowResponse;
import com.example.waterrefillapijava.report.dto.DebtAgingResponse;
import com.example.waterrefillapijava.shared.dto.PageResponse;
import com.example.waterrefillapijava.report.dto.ProductPerformanceResponse;
import com.example.waterrefillapijava.report.dto.ReconciliationResponse;
import com.example.waterrefillapijava.report.service.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	@GetMapping("/daily-sales")
	public ResponseEntity<PageResponse<DailySalesRowResponse>> getDailySales(
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search
	) {
		return ResponseEntity.ok(reportService.getDailySales(page, size, search));
	}

	@GetMapping("/product-performance")
	public ResponseEntity<PageResponse<ProductPerformanceResponse>> getProductPerformance(
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search
	) {
		return ResponseEntity.ok(reportService.getProductPerformance(page, size, search));
	}

	@GetMapping("/debt-aging")
	public ResponseEntity<PageResponse<DebtAgingResponse>> getDebtAging(
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search
	) {
		return ResponseEntity.ok(reportService.getDebtAging(page, size, search));
	}

	@GetMapping("/reconciliation")
	public ResponseEntity<PageResponse<ReconciliationResponse>> getReconciliation(
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search
	) {
		return ResponseEntity.ok(reportService.getReconciliation(page, size, search));
	}
}
