package com.example.waterrefillapijava.report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.report.dto.DailySalesRowResponse;
import com.example.waterrefillapijava.report.dto.DebtAgingResponse;
import com.example.waterrefillapijava.shared.dto.PageResponse;
import com.example.waterrefillapijava.report.dto.ProductPerformanceResponse;
import com.example.waterrefillapijava.report.dto.ReconciliationResponse;
import com.example.waterrefillapijava.order.dto.projection.DailySalesAggregate;
import com.example.waterrefillapijava.meterreading.dto.projection.GallonsByDate;
import com.example.waterrefillapijava.meterreading.dto.projection.MeterReadingValue;
import com.example.waterrefillapijava.order.dto.projection.ProductSalesAggregate;
import com.example.waterrefillapijava.meterreading.model.MeterReading;
import com.example.waterrefillapijava.customer.repository.CustomerRepository;
import com.example.waterrefillapijava.meterreading.repository.MeterReadingRepository;
import com.example.waterrefillapijava.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {

	private static final BigDecimal VARIANCE_TOLERANCE = new BigDecimal("0.10");

	private final OrderRepository orderRepository;
	private final CustomerRepository customerRepository;
	private final MeterReadingRepository meterReadingRepository;

	private static int clampPage(int page) {
		return Math.max(1, page);
	}

	private static int clampSize(int size) {
		return Math.max(1, Math.min(100, size));
	}

	private static <T> PageResponse<T> toPageResponse(List<T> content, int page, int size, int totalItems) {
		return new PageResponse<>(
			content,
			page,
			size,
			(long) totalItems,
			(int) Math.ceil((double) totalItems / size)
		);
	}

	@Transactional(readOnly = true)
	@Cacheable(value = "report:daily-sales", key = "#page + ':' + #size + ':' + (#search == null ? '' : #search)")
	public PageResponse<DailySalesRowResponse> getDailySales(int page, int size, String search) {
		page = clampPage(page);
		size = clampSize(size);

		final List<DailySalesAggregate> dailySales = orderRepository.aggregateDailySales();
		final List<GallonsByDate> gallonsByDate = orderRepository.aggregateGallonsByDate();

		final Map<LocalDate, BigDecimal> gallonsMap = gallonsByDate.stream()
			.collect(Collectors.toMap(GallonsByDate::localDate, GallonsByDate::gallons));

		List<DailySalesRowResponse> all = dailySales.stream()
			.filter(d -> search == null || search.isBlank() || d.localDate().toString().contains(search))
			.map(d -> new DailySalesRowResponse(
				d.localDate().toString(),
				d.localDate().toString(),
				d.orderCount().intValue(),
				d.revenue().setScale(2, RoundingMode.HALF_UP),
				d.cash().setScale(2, RoundingMode.HALF_UP),
				d.eWallet().setScale(2, RoundingMode.HALF_UP),
				d.credit().setScale(2, RoundingMode.HALF_UP),
				gallonsMap.getOrDefault(d.localDate(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
			))
			.sorted((a, b) -> b.date().compareTo(a.date()))
			.toList();

		final int totalItems = all.size();
		final int from = Math.min((page - 1) * size, totalItems);
		final int to = Math.min(from + size, totalItems);
		final List<DailySalesRowResponse> paged = all.subList(from, to);

		return toPageResponse(paged, page, size, totalItems);
	}

	@Transactional(readOnly = true)
	@Cacheable(value = "report:product-performance", key = "#page + ':' + #size + ':' + (#search == null ? '' : #search)")
	public PageResponse<ProductPerformanceResponse> getProductPerformance(int page, int size, String search) {
		page = clampPage(page);
		size = clampSize(size);

		final List<ProductSalesAggregate> productPerformance = orderRepository.aggregateProductPerformance();

		final BigDecimal totalRevenue = productPerformance.stream()
			.map(ProductSalesAggregate::revenue)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		List<ProductPerformanceResponse> all = productPerformance.stream()
			.filter(p -> search == null || search.isBlank()
				|| p.productName().toLowerCase().contains(search.toLowerCase()))
			.map(p -> {
				final BigDecimal sharePct = totalRevenue.compareTo(BigDecimal.ZERO) > 0
					? p.revenue().multiply(new BigDecimal("100")).divide(totalRevenue, 1, RoundingMode.HALF_UP)
					: BigDecimal.ZERO;
				return new ProductPerformanceResponse(
					p.productId(), p.productId(), p.productName(), p.productType(),
					(int) p.units(), p.revenue(), sharePct);
			})
			.toList();

		final int totalItems = all.size();
		final int from = Math.min((page - 1) * size, totalItems);
		final int to = Math.min(from + size, totalItems);
		final List<ProductPerformanceResponse> paged = all.subList(from, to);

		return toPageResponse(paged, page, size, totalItems);
	}

	@Transactional(readOnly = true)
	@Cacheable(value = "report:debt-aging", key = "#page + ':' + #size + ':' + (#search == null ? '' : #search)")
	public PageResponse<DebtAgingResponse> getDebtAging(int page, int size, String search) {
		page = clampPage(page);
		size = clampSize(size);

		final var pageResult = customerRepository.findWithDebtPaged(search,
			PageRequest.of(page - 1, size,
				Sort.by("outstandingBalance").descending()));

		final List<DebtAgingResponse> content = pageResult.getContent().stream()
			.map(c -> new DebtAgingResponse(
				c.getId(),
				c.getName(),
				c.getPhone(),
				c.getSubscriberStatus(),
				c.getBottleDebt(),
				c.getOutstandingBalance(),
				c.getOutstandingBalance()))
			.toList();

		return new PageResponse<>(
			content,
			page,
			size,
			pageResult.getTotalElements(),
			pageResult.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	@Cacheable(value = "report:reconciliation", key = "#page + ':' + #size + ':' + (#search == null ? '' : #search)")
	public PageResponse<ReconciliationResponse> getReconciliation(int page, int size, String search) {
		page = clampPage(page);
		size = clampSize(size);

		final List<GallonsByDate> gallonsByDate = orderRepository.aggregateGallonsByDate();
		final List<MeterReadingValue> readings = meterReadingRepository.findReadingValuesByDeletedFalse();

		final TreeMap<LocalDate, MeterReadingValue> sortedReadings = new TreeMap<>(Comparator.reverseOrder());
		readings.forEach(r -> sortedReadings.put(r.readingDate(), r));

		final Set<LocalDate> allDates = new HashSet<>();
		gallonsByDate.forEach(g -> allDates.add(g.localDate()));
		readings.forEach(r -> allDates.add(r.readingDate()));

		List<ReconciliationResponse> all = allDates.stream()
			.sorted(Comparator.reverseOrder())
			.filter(d -> search == null || search.isBlank() || d.toString().contains(search))
			.map(date -> {
				final BigDecimal expectedVolume = gallonsByDate.stream()
					.filter(g -> g.localDate().equals(date))
					.map(GallonsByDate::gallons)
					.findFirst()
					.orElse(BigDecimal.ZERO);

				final MeterReadingValue reading = sortedReadings.get(date);
				final MeterReadingValue previous = sortedReadings.lowerEntry(date) != null
					? sortedReadings.lowerEntry(date).getValue()
					: null;

				final BigDecimal actualThroughput = (reading != null && previous != null)
					? reading.meterValue().subtract(previous.meterValue())
					: null;
				final BigDecimal variance = actualThroughput == null
					? null
					: actualThroughput.subtract(expectedVolume).setScale(2, RoundingMode.HALF_UP);
				final BigDecimal variancePct = variance != null && expectedVolume.compareTo(BigDecimal.ZERO) > 0
					? variance.multiply(new BigDecimal("100")).divide(expectedVolume, 1, RoundingMode.HALF_UP)
					: null;
				final boolean flagged = isFlagged(expectedVolume, actualThroughput, variance);
				final String status = actualThroughput == null ? "No Data" : flagged ? "Flagged" : "OK";

				return new ReconciliationResponse(date.toString(), date.toString(),
					expectedVolume, actualThroughput, variance, variancePct, flagged, status);
			})
			.toList();

		final int totalItems = all.size();
		final int from = Math.min((page - 1) * size, totalItems);
		final int to = Math.min(from + size, totalItems);
		final List<ReconciliationResponse> paged = all.subList(from, to);

		return toPageResponse(paged, page, size, totalItems);
	}

	private boolean isFlagged(BigDecimal expectedVolume, BigDecimal actualThroughput, BigDecimal variance) {
		if (actualThroughput == null) return false;
		if (expectedVolume.compareTo(BigDecimal.ZERO) <= 0) return actualThroughput.compareTo(BigDecimal.ZERO) > 0;
		return variance.abs().divide(expectedVolume, 10, RoundingMode.HALF_UP).compareTo(VARIANCE_TOLERANCE) > 0;
	}
}
