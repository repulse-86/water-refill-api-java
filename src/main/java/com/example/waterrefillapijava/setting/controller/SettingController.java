package com.example.waterrefillapijava.setting.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.waterrefillapijava.setting.dto.SettingResponse;
import com.example.waterrefillapijava.setting.dto.UpdateSettingRequest;
import com.example.waterrefillapijava.setting.model.Setting;
import com.example.waterrefillapijava.setting.service.SettingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SettingController {

	private final SettingService settingService;

	@GetMapping("/settings")
	public ResponseEntity<SettingResponse> getSettings() {
		return ResponseEntity.ok(new SettingResponse(settingService.getOrCreate()));
	}

	@PutMapping("/settings")
	public ResponseEntity<SettingResponse> updateSettings(@Valid @RequestBody final UpdateSettingRequest request) {
		final Setting updated = settingService.update(
			request.storeName(),
			request.storeAddress(),
			request.storePhone(),
			request.currency(),
			request.lowStockThreshold()
		);

		log.info("Settings updated: storeName={}", updated.getStoreName());

		return ResponseEntity.ok(new SettingResponse(updated));
	}
}
