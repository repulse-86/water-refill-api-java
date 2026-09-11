package com.example.waterrefillapijava.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.dto.DailySalesRowResponse;
import com.example.waterrefillapijava.dto.DebtAgingResponse;
import com.example.waterrefillapijava.dto.PageResponse;
import com.example.waterrefillapijava.dto.ProductPerformanceResponse;
import com.example.waterrefillapijava.dto.ReconciliationResponse;
import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.MeterReading;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderItem;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductType;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.MeterReadingRepository;
import com.example.waterrefillapijava.repository.OrderRepository;

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

		final List<Order> completedOrders = orderRepository.findByStatusWithItems(OrderStatus.completed);

		final Map<String, DailySalesAccumulator> byDay = new HashMap<>();

		for (Order order : completedOrders) {
			if (order.getCreatedAt() == null) continue;
			final String date = order.getCreatedAt().toLocalDate().toString();
			if (search != null && !search.isBlank() && !date.contains(search)) continue;
			final DailySalesAccumulator row = byDay.computeIfAbsent(date, DailySalesAccumulator::new);

			row.orderCount++;
			row.revenue = row.revenue.add(order.getTotalAmount());
			switch (order.getPaymentMethod()) {
				case cash -> row.cash = row.cash.add(order.getTotalAmount());
				case e_wallet -> row.eWallet = row.eWallet.add(order.getTotalAmount());
				case credit -> row.credit = row.credit.add(order.getTotalAmount());
			}

			for (OrderItem item : order.getItems()) {
				final Product product = item.getProduct();
				if (product != null && product.getType() == ProductType.water_refill && product.getVolumeGallons() != null) {
					row.gallons = row.gallons.add(
						product.getVolumeGallons().multiply(BigDecimal.valueOf(item.getQuantity())));
				}
			}
		}

		final List<DailySalesRowResponse> all = byDay.values().stream()
			.map(DailySalesAccumulator::toResponse)
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

		final List<Order> completedOrders = orderRepository.findByStatusWithItems(OrderStatus.completed);

		final record ProductAcc(String name, String type, int units, BigDecimal revenue) {
			ProductAcc add(int qty, BigDecimal sub) {
				return new ProductAcc(name, type, units + qty, revenue.add(sub));
			}
		}

		final Map<Long, ProductAcc> byId = new HashMap<>();
		for (Order order : completedOrders) {
			for (OrderItem item : order.getItems()) {
				final Product product = item.getProduct();
				if (product == null) continue;
				if (search != null && !search.isBlank()
					&& !product.getName().toLowerCase().contains(search.toLowerCase())) continue;
				final Long pid = product.getId();
				byId.merge(pid, new ProductAcc(product.getName(), product.getType().name(), item.getQuantity(), item.getSubtotal()),
					(existing, incoming) -> existing.add(incoming.units, incoming.revenue));
			}
		}

		final BigDecimal totalRevenue = byId.values().stream()
			.map(ProductAcc::revenue)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		final List<ProductPerformanceResponse> all = byId.entrySet().stream()
			.sorted((a, b) -> b.getValue().revenue().compareTo(a.getValue().revenue()))
			.map(e -> {
				final ProductAcc acc = e.getValue();
				final BigDecimal sharePct = totalRevenue.compareTo(BigDecimal.ZERO) > 0
					? acc.revenue().multiply(new BigDecimal("100")).divide(totalRevenue, 1, RoundingMode.HALF_UP)
					: BigDecimal.ZERO;
				return new ProductPerformanceResponse(
					e.getKey(), e.getKey(), acc.name(), acc.type(), acc.units(), acc.revenue(), sharePct);
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

		List<Customer> customers = customerRepository.findWithDebt();
		if (search != null && !search.isBlank()) {
			final String lower = search.toLowerCase();
			customers = customers.stream()
				.filter(c -> c.getName().toLowerCase().contains(lower) || c.getPhone().toLowerCase().contains(lower))
				.toList();
		}

		final List<DebtAgingResponse> all = customers.stream()
			.map(c -> new DebtAgingResponse(
				c.getId(),
				c.getName(),
				c.getPhone(),
				c.getSubscriberStatus(),
				c.getBottleDebt(),
				c.getOutstandingBalance(),
				c.getOutstandingBalance()))
			.toList();

		final int totalItems = all.size();
		final int from = Math.min((page - 1) * size, totalItems);
		final int to = Math.min(from + size, totalItems);
		final List<DebtAgingResponse> paged = all.subList(from, to);

		return toPageResponse(paged, page, size, totalItems);
	}

	@Transactional(readOnly = true)
	@Cacheable(value = "report:reconciliation", key = "#page + ':' + #size + ':' + (#search == null ? '' : #search)")
	public PageResponse<ReconciliationResponse> getReconciliation(int page, int size, String search) {
		page = clampPage(page);
		size = clampSize(size);

		final List<Order> completedOrders = orderRepository.findByStatusWithItems(OrderStatus.completed);
		final List<MeterReading> readings = meterReadingRepository.findAll();

		final Set<String> dates = new HashSet<>();
		for (Order order : completedOrders) {
			if (order.getCreatedAt() != null) {
				dates.add(order.getCreatedAt().toLocalDate().toString());
			}
		}
		for (MeterReading reading : readings) {
			dates.add(reading.getReadingDate().toString());
		}

		final List<MeterReading> sortedReadings = new ArrayList<>(readings);
		sortedReadings.sort((a, b) -> a.getReadingDate().compareTo(b.getReadingDate()));

		List<String> sortedDates = dates.stream()
			.sorted((a, b) -> b.compareTo(a))
			.toList();
		if (search != null && !search.isBlank()) {
			sortedDates = sortedDates.stream()
				.filter(d -> d.contains(search))
				.toList();
		}

		final List<ReconciliationResponse> all = sortedDates.stream()
			.map(date -> {
				final LocalDate dateObj = LocalDate.parse(date);
				final int index = findReadingIndex(sortedReadings, dateObj);
				final MeterReading reading = index >= 0 ? sortedReadings.get(index) : null;
				final MeterReading previous = index > 0 ? sortedReadings.get(index - 1) : null;

				final BigDecimal expectedVolume = computeExpectedVolume(completedOrders, dateObj);
				final BigDecimal actualThroughput = reading != null && previous != null
					? reading.getMeterValue().subtract(previous.getMeterValue())
					: null;
				final BigDecimal variance = actualThroughput == null
					? null
					: actualThroughput.subtract(expectedVolume).setScale(2, RoundingMode.HALF_UP);
				final BigDecimal variancePct = variance != null && expectedVolume.compareTo(BigDecimal.ZERO) > 0
					? variance.multiply(new BigDecimal("100")).divide(expectedVolume, 1, RoundingMode.HALF_UP)
					: null;
				final boolean flagged = isFlagged(expectedVolume, actualThroughput, variance);
				final String status = actualThroughput == null ? "No Data" : flagged ? "Flagged" : "OK";

				return new ReconciliationResponse(date, date, expectedVolume, actualThroughput, variance, variancePct, flagged, status);
			})
			.toList();

		final int totalItems = all.size();
		final int from = Math.min((page - 1) * size, totalItems);
		final int to = Math.min(from + size, totalItems);
		final List<ReconciliationResponse> paged = all.subList(from, to);

		return toPageResponse(paged, page, size, totalItems);
	}

	private int findReadingIndex(List<MeterReading> sortedReadings, LocalDate date) {
		for (int i = 0; i < sortedReadings.size(); i++) {
			if (sortedReadings.get(i).getReadingDate().equals(date)) return i;
		}
		return -1;
	}

	private BigDecimal computeExpectedVolume(List<Order> completedOrders, LocalDate date) {
		BigDecimal total = BigDecimal.ZERO;
		for (Order order : completedOrders) {
			if (order.getCreatedAt() != null && order.getCreatedAt().toLocalDate().equals(date)) {
				for (OrderItem item : order.getItems()) {
					final Product product = item.getProduct();
					if (product != null && product.getType() == ProductType.water_refill && product.getVolumeGallons() != null) {
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

	private static class DailySalesAccumulator {
		String date;
		int orderCount;
		BigDecimal revenue = BigDecimal.ZERO;
		BigDecimal cash = BigDecimal.ZERO;
		BigDecimal eWallet = BigDecimal.ZERO;
		BigDecimal credit = BigDecimal.ZERO;
		BigDecimal gallons = BigDecimal.ZERO;

		DailySalesAccumulator(String date) { this.date = date; }

		DailySalesRowResponse toResponse() {
			return new DailySalesRowResponse(
				date, date, orderCount,
				revenue.setScale(2, RoundingMode.HALF_UP),
				cash.setScale(2, RoundingMode.HALF_UP),
				eWallet.setScale(2, RoundingMode.HALF_UP),
				credit.setScale(2, RoundingMode.HALF_UP),
				gallons.setScale(2, RoundingMode.HALF_UP));
		}
	}
}
