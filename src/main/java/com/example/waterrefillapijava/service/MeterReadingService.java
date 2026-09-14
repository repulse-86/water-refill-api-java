package com.example.waterrefillapijava.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.dto.MeterReadingResponse;
import com.example.waterrefillapijava.dto.PageResponse;
import com.example.waterrefillapijava.dto.projection.GallonsByDate;
import com.example.waterrefillapijava.dto.projection.MeterReadingValue;
import com.example.waterrefillapijava.exception.ConflictException;
import com.example.waterrefillapijava.exception.FieldValidationException;
import com.example.waterrefillapijava.exception.NotFoundException;
import com.example.waterrefillapijava.model.MeterReading;
import com.example.waterrefillapijava.repository.MeterReadingRepository;
import com.example.waterrefillapijava.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MeterReadingService {

	private static final BigDecimal VARIANCE_TOLERANCE = new BigDecimal("0.10");

	private final MeterReadingRepository meterReadingRepository;
	private final OrderRepository orderRepository;

	@Transactional(readOnly = true)
	public PageResponse<MeterReadingResponse> listAll(int page, int size, String search) {
		final Pageable pageable = PageRequest.of(
			Math.max(0, page - 1),
			Math.max(1, Math.min(100, size)),
			Sort.by(Sort.Direction.DESC, "readingDate")
		);

		final Page<MeterReading> pageResult;
		if (search != null && !search.isBlank()) {
			pageResult = meterReadingRepository.findBySearchAndDeletedFalse(search, pageable);
		} else {
			pageResult = meterReadingRepository.findByDeletedFalse(pageable);
		}

		final List<GallonsByDate> gallonsByDate = orderRepository.aggregateGallonsByDate();
		final List<MeterReadingValue> allReadings = meterReadingRepository.findReadingValuesByDeletedFalse();
		final TreeMap<LocalDate, MeterReadingValue> sortedReadings = buildSortedReadings(allReadings);
		final Map<LocalDate, BigDecimal> gallonsMap = gallonsByDate.stream()
			.collect(Collectors.toMap(GallonsByDate::localDate, GallonsByDate::gallons));

		final List<MeterReadingResponse> responses = pageResult.getContent().stream()
			.map(r -> enrichOne(r, sortedReadings, gallonsMap))
			.toList();

		return new PageResponse<>(
			responses,
			pageResult.getNumber() + 1,
			pageResult.getSize(),
			pageResult.getTotalElements(),
			pageResult.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	public MeterReadingResponse findById(Long id) {
		final MeterReading reading = meterReadingRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Meter reading not found."));
		if (reading.isDeleted()) {
			throw new NotFoundException("Meter reading not found.");
		}

		final MeterReadingValue previous = meterReadingRepository
			.findPreviousReadingValues(reading.getReadingDate()).stream().findFirst().orElse(null);
		final List<GallonsByDate> gallonsByDate = orderRepository.aggregateGallonsByDate();
		final Map<LocalDate, BigDecimal> gallonsMap = gallonsByDate.stream()
			.collect(Collectors.toMap(GallonsByDate::localDate, GallonsByDate::gallons));

		return enrichOneSingle(reading, previous, gallonsMap);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:reconciliation")
	})
	public MeterReadingResponse create(String readingDateStr, BigDecimal meterValue, String notes) {
		final LocalDate readingDate = parseDate(readingDateStr);

		if (meterValue == null || meterValue.compareTo(BigDecimal.ZERO) < 0) {
			throw FieldValidationException.builder()
				.add("meter_value", "The meter value must be a positive number.")
				.build();
		}

		if (meterReadingRepository.existsByReadingDate(readingDate)) {
			throw new ConflictException("A reading for this date has already been recorded.");
		}

		final MeterReading reading = MeterReading.builder()
			.readingDate(readingDate)
			.meterValue(meterValue)
			.notes(notes != null ? notes.trim() : null)
			.build();

		meterReadingRepository.save(reading);

		final MeterReadingValue previous = meterReadingRepository
			.findPreviousReadingValues(readingDate).stream().findFirst().orElse(null);
		final List<GallonsByDate> gallonsByDate = orderRepository.aggregateGallonsByDate();
		final Map<LocalDate, BigDecimal> gallonsMap = gallonsByDate.stream()
			.collect(Collectors.toMap(GallonsByDate::localDate, GallonsByDate::gallons));

		return enrichOneSingle(reading, previous, gallonsMap);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:reconciliation")
	})
	public MeterReadingResponse update(Long id, String readingDateStr, BigDecimal meterValue, String notes) {
		final MeterReading reading = meterReadingRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Meter reading not found."));

		final LocalDate readingDate = parseDate(readingDateStr);

		if (meterValue == null || meterValue.compareTo(BigDecimal.ZERO) < 0) {
			throw FieldValidationException.builder()
				.add("meter_value", "The meter value must be a positive number.")
				.build();
		}

		if (!reading.getReadingDate().equals(readingDate) && meterReadingRepository.existsByReadingDateAndIdNot(readingDate, id)) {
			throw new ConflictException("A reading for this date has already been recorded.");
		}

		reading.setReadingDate(readingDate);
		reading.setMeterValue(meterValue);
		reading.setNotes(notes != null ? notes.trim() : null);

		meterReadingRepository.save(reading);

		final MeterReadingValue previous = meterReadingRepository
			.findPreviousReadingValues(readingDate).stream().findFirst().orElse(null);
		final List<GallonsByDate> gallonsByDate = orderRepository.aggregateGallonsByDate();
		final Map<LocalDate, BigDecimal> gallonsMap = gallonsByDate.stream()
			.collect(Collectors.toMap(GallonsByDate::localDate, GallonsByDate::gallons));

		return enrichOneSingle(reading, previous, gallonsMap);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:reconciliation")
	})
	public void delete(Long id) {
		final MeterReading reading = meterReadingRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Meter reading not found."));
		reading.setDeleted(true);
		reading.setDeletedAt(LocalDateTime.now());
		meterReadingRepository.save(reading);
	}

	@Transactional(readOnly = true)
	public PageResponse<MeterReadingResponse> archiveList(int page, int size, String search) {
		final Pageable pageable = PageRequest.of(
			Math.max(0, page - 1),
			Math.max(1, Math.min(100, size)),
			Sort.by(Sort.Direction.DESC, "readingDate")
		);

		final Page<MeterReading> pageResult;
		if (search != null && !search.isBlank()) {
			pageResult = meterReadingRepository.findBySearchAndDeletedTrue(search, pageable);
		} else {
			pageResult = meterReadingRepository.findByDeletedTrue(pageable);
		}

		final List<GallonsByDate> gallonsByDate = orderRepository.aggregateGallonsByDate();
		final List<MeterReadingValue> allReadings = meterReadingRepository.findReadingValuesByDeletedFalse();
		final TreeMap<LocalDate, MeterReadingValue> sortedReadings = buildSortedReadings(allReadings);
		final Map<LocalDate, BigDecimal> gallonsMap = gallonsByDate.stream()
			.collect(Collectors.toMap(GallonsByDate::localDate, GallonsByDate::gallons));

		final List<MeterReadingResponse> responses = pageResult.getContent().stream()
			.map(r -> enrichOne(r, sortedReadings, gallonsMap))
			.toList();

		return new PageResponse<>(
			responses,
			pageResult.getNumber() + 1,
			pageResult.getSize(),
			pageResult.getTotalElements(),
			pageResult.getTotalPages()
		);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:reconciliation")
	})
	public void restore(Long id) {
		final MeterReading reading = meterReadingRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Meter reading not found."));
		if (!reading.isDeleted()) {
			throw new ConflictException("Meter reading is not archived.");
		}
		if (meterReadingRepository.existsByReadingDateAndIdNot(reading.getReadingDate(), id)) {
			throw new ConflictException("A reading for this date already exists.");
		}
		reading.setDeleted(false);
		reading.setDeletedAt(null);
		meterReadingRepository.save(reading);
	}

	@Transactional
	public void permanentDelete(Long id) {
		final MeterReading reading = meterReadingRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Meter reading not found."));
		if (!reading.isDeleted()) {
			throw new ConflictException("Meter reading must be archived before permanent deletion.");
		}
		meterReadingRepository.deleteById(id);
	}

	private LocalDate parseDate(String dateStr) {
		if (dateStr == null || dateStr.isBlank()) {
			throw FieldValidationException.builder()
				.add("reading_date", "The reading date field is required.")
				.build();
		}
		try {
			return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
		} catch (DateTimeParseException e) {
			throw FieldValidationException.builder()
				.add("reading_date", "The reading date must be a valid date.")
				.build();
		}
	}

	private TreeMap<LocalDate, MeterReadingValue> buildSortedReadings(List<MeterReadingValue> readings) {
		final TreeMap<LocalDate, MeterReadingValue> sorted = new TreeMap<>(Comparator.reverseOrder());
		readings.forEach(r -> sorted.put(r.readingDate(), r));
		return sorted;
	}

	private MeterReadingResponse enrichOne(MeterReading reading,
			TreeMap<LocalDate, MeterReadingValue> sortedReadings,
			Map<LocalDate, BigDecimal> gallonsMap) {

		final MeterReadingValue previous = sortedReadings.lowerEntry(reading.getReadingDate()) != null
			? sortedReadings.lowerEntry(reading.getReadingDate()).getValue()
			: null;

		return buildResponse(reading, previous, gallonsMap);
	}

	private MeterReadingResponse enrichOneSingle(MeterReading reading,
			MeterReadingValue previous,
			Map<LocalDate, BigDecimal> gallonsMap) {

		return buildResponse(reading, previous, gallonsMap);
	}

	private MeterReadingResponse buildResponse(MeterReading reading,
			MeterReadingValue previous,
			Map<LocalDate, BigDecimal> gallonsMap) {

		final BigDecimal expectedVolume = gallonsMap.getOrDefault(reading.getReadingDate(), BigDecimal.ZERO);
		final BigDecimal actualThroughput = previous == null
			? null
			: reading.getMeterValue().subtract(previous.meterValue());
		final BigDecimal variance = actualThroughput == null
			? null
			: actualThroughput.subtract(expectedVolume).setScale(2, RoundingMode.HALF_UP);
		final BigDecimal variancePct = variance != null && expectedVolume.compareTo(BigDecimal.ZERO) > 0
			? variance.multiply(new BigDecimal("100")).divide(expectedVolume, 1, RoundingMode.HALF_UP)
			: null;
		final boolean flagged = isFlagged(expectedVolume, actualThroughput, variance);

		return new MeterReadingResponse(
			reading.getId(),
			reading.getReadingDate().toString(),
			reading.getMeterValue(),
			reading.getNotes(),
			previous == null ? null : previous.meterValue(),
			expectedVolume,
			actualThroughput,
			variance,
			variancePct,
			flagged,
			reading.getCreatedAt() != null ? reading.getCreatedAt().toString() : null,
			reading.getModifiedAt() != null ? reading.getModifiedAt().toString() : null,
			reading.getDeletedAt() != null ? reading.getDeletedAt().toString() : null
		);
	}

	private boolean isFlagged(BigDecimal expectedVolume, BigDecimal actualThroughput, BigDecimal variance) {
		if (actualThroughput == null) return false;
		if (expectedVolume.compareTo(BigDecimal.ZERO) <= 0) return actualThroughput.compareTo(BigDecimal.ZERO) > 0;
		return variance.abs().divide(expectedVolume, 10, RoundingMode.HALF_UP).compareTo(VARIANCE_TOLERANCE) > 0;
	}
}
