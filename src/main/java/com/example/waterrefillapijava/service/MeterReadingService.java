package com.example.waterrefillapijava.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.dto.MeterReadingResponse;
import com.example.waterrefillapijava.dto.PageResponse;
import com.example.waterrefillapijava.exception.ConflictException;
import com.example.waterrefillapijava.exception.FieldValidationException;
import com.example.waterrefillapijava.exception.NotFoundException;
import com.example.waterrefillapijava.model.MeterReading;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderItem;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductType;
import com.example.waterrefillapijava.repository.MeterReadingRepository;
import com.example.waterrefillapijava.repository.OrderItemRepository;
import com.example.waterrefillapijava.repository.OrderRepository;
import com.example.waterrefillapijava.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MeterReadingService {

	private static final BigDecimal VARIANCE_TOLERANCE = new BigDecimal("0.10");

	private final MeterReadingRepository meterReadingRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;

	@Transactional(readOnly = true)
	public PageResponse<MeterReadingResponse> listAll(int page, int size, String search) {
		final Pageable pageable = PageRequest.of(
			Math.max(0, page - 1),
			Math.max(1, Math.min(100, size)),
			Sort.by(Sort.Direction.DESC, "readingDate")
		);

		final Page<MeterReading> pageResult;
		if (search != null && !search.isBlank()) {
			pageResult = meterReadingRepository.findBySearch(search, pageable);
		} else {
			pageResult = meterReadingRepository.findAll(pageable);
		}

		final List<MeterReading> all = meterReadingRepository.findAll(Sort.by(Sort.Direction.DESC, "readingDate"));
		final List<Order> completedOrders = orderRepository.findByStatus(OrderStatus.completed, Pageable.unpaged()).getContent();
		final List<Product> products = productRepository.findAll();

		final List<MeterReadingResponse> responses = pageResult.getContent().stream()
			.map(r -> enrichOne(r, all, completedOrders, products))
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
		final List<MeterReading> all = meterReadingRepository.findAll();
		return enrichOne(reading, all);
	}

	@Transactional
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

		final List<MeterReading> all = meterReadingRepository.findAll();
		return enrichOne(reading, all);
	}

	@Transactional
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

		final List<MeterReading> all = meterReadingRepository.findAll();
		return enrichOne(reading, all);
	}

	@Transactional
	public void delete(Long id) {
		if (!meterReadingRepository.existsById(id)) {
			throw new NotFoundException("Meter reading not found.");
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

	private MeterReadingResponse enrichOne(MeterReading reading, List<MeterReading> all) {
		final List<Order> completedOrders = orderRepository.findByStatus(OrderStatus.completed, Pageable.unpaged()).getContent();
		final List<Product> products = productRepository.findAll();
		return enrichOne(reading, all, completedOrders, products);
	}

	private MeterReadingResponse enrichOne(MeterReading reading, List<MeterReading> all,
			List<Order> completedOrders, List<Product> products) {

		final MeterReading previous = getPreviousReading(all, reading.getReadingDate());
		final BigDecimal expectedVolume = computeExpectedVolume(completedOrders, products, reading.getReadingDate());
		final BigDecimal actualThroughput = previous == null
			? null
			: reading.getMeterValue().subtract(previous.getMeterValue());
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
			previous == null ? null : previous.getMeterValue(),
			expectedVolume,
			actualThroughput,
			variance,
			variancePct,
			flagged,
			reading.getCreatedAt() != null ? reading.getCreatedAt().toString() : null,
			reading.getModifiedAt() != null ? reading.getModifiedAt().toString() : null
		);
	}

	private MeterReading getPreviousReading(List<MeterReading> readings, LocalDate date) {
		return readings.stream()
			.filter(r -> r.getReadingDate().isBefore(date))
			.sorted((MeterReading a, MeterReading b) -> b.getReadingDate().compareTo(a.getReadingDate()))
			.findFirst()
			.orElse(null);
	}

	private BigDecimal computeExpectedVolume(List<Order> completedOrders, List<Product> products, LocalDate date) {
		final java.util.Map<Long, Product> productById = new java.util.HashMap<>();
		for (Product p : products) {
			if (p.getType() == ProductType.water_refill) {
				productById.put(p.getId(), p);
			}
		}

		BigDecimal total = BigDecimal.ZERO;
		for (Order order : completedOrders) {
			if (order.getCreatedAt() != null && order.getCreatedAt().toLocalDate().equals(date)) {
				final List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
				for (OrderItem item : items) {
					final Product product = productById.get(item.getProduct().getId());
					if (product != null && product.getVolumeGallons() != null) {
						total = total.add(product.getVolumeGallons().multiply(BigDecimal.valueOf(item.getQuantity())));
					}
				}
			}
		}
		return total;
	}

	private boolean isFlagged(BigDecimal expectedVolume, BigDecimal actualThroughput, BigDecimal variance) {
		if (actualThroughput == null) return false;
		if (expectedVolume.compareTo(BigDecimal.ZERO) <= 0) return actualThroughput.compareTo(BigDecimal.ZERO) > 0;
		return variance.abs().divide(expectedVolume, 10, RoundingMode.HALF_UP).compareTo(VARIANCE_TOLERANCE) > 0;
	}
}
