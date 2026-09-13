package com.example.waterrefillapijava.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.waterrefillapijava.dto.MessageResponse;
import com.example.waterrefillapijava.dto.MeterReadingRequest;
import com.example.waterrefillapijava.dto.MeterReadingResponse;
import com.example.waterrefillapijava.dto.PageResponse;
import com.example.waterrefillapijava.service.MeterReadingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/meter-readings")
@RequiredArgsConstructor
@Slf4j
public class MeterReadingController {

	private final MeterReadingService meterReadingService;

	@GetMapping
	public ResponseEntity<PageResponse<MeterReadingResponse>> list(
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search
	) {
		return ResponseEntity.ok(meterReadingService.listAll(page, size, search));
	}

	@GetMapping("/{id}")
	public ResponseEntity<MeterReadingResponse> get(@PathVariable final Long id) {
		return ResponseEntity.ok(meterReadingService.findById(id));
	}

	@PostMapping
	public ResponseEntity<MeterReadingResponse> create(@Valid @RequestBody final MeterReadingRequest request) {
		final MeterReadingResponse reading = meterReadingService.create(
			request.readingDate(), request.meterValue(), request.notes()
		);

		log.info("Meter reading created: id={}, date={}", reading.id(), reading.readingDate());

		return ResponseEntity.ok(reading);
	}

	@PutMapping("/{id}")
	public ResponseEntity<MeterReadingResponse> update(
		@PathVariable final Long id,
		@Valid @RequestBody final MeterReadingRequest request
	) {
		final MeterReadingResponse reading = meterReadingService.update(
			id, request.readingDate(), request.meterValue(), request.notes()
		);

		log.info("Meter reading updated: id={}, date={}", reading.id(), reading.readingDate());

		return ResponseEntity.ok(reading);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable final Long id) {
		meterReadingService.delete(id);

		log.info("Meter reading deleted: id={}", id);

		return ResponseEntity.ok(new MessageResponse("Meter reading deleted successfully."));
	}

	@GetMapping("/deleted")
	public ResponseEntity<PageResponse<MeterReadingResponse>> listDeleted(
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search
	) {
		return ResponseEntity.ok(meterReadingService.archiveList(page, size, search));
	}

	@PostMapping("/{id}/restore")
	public ResponseEntity<MeterReadingResponse> restore(@PathVariable final Long id) {
		meterReadingService.restore(id);

		log.info("Meter reading restored: id={}", id);

		return ResponseEntity.ok(meterReadingService.findById(id));
	}

	@DeleteMapping("/{id}/permanent")
	public ResponseEntity<?> permanentDelete(@PathVariable final Long id) {
		meterReadingService.permanentDelete(id);

		log.info("Meter reading permanently deleted: id={}", id);

		return ResponseEntity.ok(new com.example.waterrefillapijava.dto.MessageResponse("Meter reading permanently deleted successfully."));
	}
}
