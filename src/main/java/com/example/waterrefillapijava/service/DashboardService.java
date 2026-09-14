package com.example.waterrefillapijava.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.dto.DashboardResponse;
import com.example.waterrefillapijava.dto.DashboardResponse.DailySalesRow;
import com.example.waterrefillapijava.dto.DashboardResponse.LowStockProduct;
import com.example.waterrefillapijava.dto.DashboardResponse.PaymentMix;
import com.example.waterrefillapijava.dto.DashboardResponse.PendingOrder;
import com.example.waterrefillapijava.dto.DashboardResponse.QuickStats;
import com.example.waterrefillapijava.dto.DashboardResponse.TodaySummary;
import com.example.waterrefillapijava.dto.DashboardResponse.TopProduct;
import com.example.waterrefillapijava.dto.projection.DailySalesAggregate;
import com.example.waterrefillapijava.dto.projection.GallonsByDate;
import com.example.waterrefillapijava.dto.projection.MeterReadingValue;
import com.example.waterrefillapijava.dto.projection.PaymentMixAggregate;
import com.example.waterrefillapijava.dto.projection.ProductSalesAggregate;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.MeterReadingRepository;
import com.example.waterrefillapijava.repository.OrderRepository;
import com.example.waterrefillapijava.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

	private final OrderRepository orderRepository;
	private final ProductRepository productRepository;
	private final CustomerRepository customerRepository;
	private final MeterReadingRepository meterReadingRepository;

	@Transactional(readOnly = true)
	@Cacheable("dashboard")
	public DashboardResponse getDashboard() {
		final LocalDate today = LocalDate.now();

		final List<DailySalesAggregate> dailySales = orderRepository.aggregateDailySales();
		final List<GallonsByDate> gallonsByDate = orderRepository.aggregateGallonsByDate();
		final List<ProductSalesAggregate> productPerformance = orderRepository.aggregateProductPerformance();
		final List<PaymentMixAggregate> paymentMixData = orderRepository.aggregatePaymentMix();

		final Map<LocalDate, BigDecimal> gallonsMap = gallonsByDate.stream()
			.collect(Collectors.toMap(GallonsByDate::localDate, GallonsByDate::gallons));

		final TodaySummary todaySummary = buildTodaySummary(dailySales, gallonsMap, today);
		final int activeCustomerCount = (int) customerRepository.countBySubscriberStatus("active");
		final int pendingOrderCount = (int) orderRepository.countByStatusNotAndDeletedFalse(OrderStatus.completed);
		final int bottlesReturned = orderRepository.sumBottlesReturned();

		final QuickStats quickStats = buildQuickStats(today, gallonsMap, bottlesReturned, activeCustomerCount, pendingOrderCount);
		final List<PendingOrder> pendingOrders = buildPendingOrders();
		final List<LowStockProduct> lowStock = buildLowStock();
		final List<DailySalesRow> salesTrend = buildSalesTrend(dailySales, gallonsMap);
		final List<TopProduct> topProducts = buildTopProducts(productPerformance);
		final List<PaymentMix> paymentMix = buildPaymentMix(paymentMixData);

		return new DashboardResponse(
			todaySummary,
			quickStats,
			pendingOrders,
			lowStock,
			salesTrend,
			topProducts,
			paymentMix
		);
	}

	private TodaySummary buildTodaySummary(
		List<DailySalesAggregate> dailySales,
		Map<LocalDate, BigDecimal> gallonsMap,
		LocalDate today
	) {

		final DailySalesAggregate todayRow = dailySales.stream()
			.filter(d -> d.localDate().equals(today))
			.findFirst()
			.orElse(null);

		final BigDecimal gallons = gallonsMap.getOrDefault(today, BigDecimal.ZERO);

		if (todayRow == null) {
			return new TodaySummary(
				today.toString(),
				BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
				0,
				BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
				BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
				BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
				gallons.setScale(2, RoundingMode.HALF_UP)
			);
		}

		return new TodaySummary(
			today.toString(),
			todayRow.revenue().setScale(2, RoundingMode.HALF_UP),
			todayRow.orderCount().intValue(),
			todayRow.cash().setScale(2, RoundingMode.HALF_UP),
			todayRow.eWallet().setScale(2, RoundingMode.HALF_UP),
			todayRow.credit().setScale(2, RoundingMode.HALF_UP),
			gallons.setScale(2, RoundingMode.HALF_UP)
		);
	}

	private QuickStats buildQuickStats(
		LocalDate today,
		Map<LocalDate,
		BigDecimal> gallonsMap,
		int bottlesReturned,
		int activeCustomers,
		int pendingOrderCount
	) {

		final List<MeterReadingValue> readings = meterReadingRepository.findReadingValuesByDeletedFalse();
		BigDecimal gallonsPumped;

		if (readings.size() >= 2) {
			final MeterReadingValue latest = readings.get(0);
			final MeterReadingValue previous = readings.get(1);
			gallonsPumped = latest.meterValue().subtract(previous.meterValue());
		} else {
			gallonsPumped = gallonsMap.getOrDefault(today, BigDecimal.ZERO);
		}

		return new QuickStats(gallonsPumped.setScale(2, RoundingMode.HALF_UP), bottlesReturned, activeCustomers, pendingOrderCount);
	}

	private List<PendingOrder> buildPendingOrders() {
		return orderRepository.findByStatusNotAndDeletedFalseOrderByCreatedAtDesc(OrderStatus.completed).stream()
			.map(o -> new PendingOrder(
				o.getId(),
				o.getCustomer() != null ? o.getCustomer().getName() : "Walk-in",
				o.getOrderType().name(),
				o.getStatus().name(),
				o.getTotalAmount(),
				o.getCreatedAt() != null ? o.getCreatedAt().toString() : null
			))
			.toList();
	}

	private List<LowStockProduct> buildLowStock() {
		return productRepository.findLowStock().stream()
			.map(p -> new LowStockProduct(
				p.getId(),
				p.getName(),
				p.getType().name(),
				p.getStockQuantity(),
				p.getReorderPoint()
			))
			.toList();
	}

	private List<DailySalesRow> buildSalesTrend(List<DailySalesAggregate> dailySales,
			Map<LocalDate, BigDecimal> gallonsMap) {

		return dailySales.stream()
			.sorted(Comparator.comparing(DailySalesAggregate::localDate))
			.map(d -> new DailySalesRow(
				d.localDate().toString(),
				d.localDate().toString(),
				d.orderCount().intValue(),
				d.revenue().setScale(2, RoundingMode.HALF_UP),
				d.cash().setScale(2, RoundingMode.HALF_UP),
				d.eWallet().setScale(2, RoundingMode.HALF_UP),
				d.credit().setScale(2, RoundingMode.HALF_UP),
				gallonsMap.getOrDefault(d.localDate(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
			))
			.toList();
	}

	private List<TopProduct> buildTopProducts(List<ProductSalesAggregate> productPerformance) {
		final BigDecimal totalRevenue = productPerformance.stream()
			.map(ProductSalesAggregate::revenue)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		return productPerformance.stream()
			.limit(5)
			.map(p -> {
				final BigDecimal sharePct = totalRevenue.compareTo(BigDecimal.ZERO) > 0
					? p.revenue().multiply(new BigDecimal("100")).divide(totalRevenue, 1, RoundingMode.HALF_UP)
					: BigDecimal.ZERO;
				return new TopProduct(p.productId(), p.productName(), p.revenue(), (int) p.units(), sharePct);
			})
			.toList();
	}

	private List<PaymentMix> buildPaymentMix(List<PaymentMixAggregate> paymentMixData) {
		return List.of(
			new PaymentMix("cash", "Cash", paymentMixData.stream()
				.filter(p -> "cash".equals(p.paymentMethod()))
				.map(PaymentMixAggregate::total)
				.findFirst().orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)),
			new PaymentMix("e_wallet", "E-Wallet", paymentMixData.stream()
				.filter(p -> "e_wallet".equals(p.paymentMethod()))
				.map(PaymentMixAggregate::total)
				.findFirst().orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)),
			new PaymentMix("credit", "Credit", paymentMixData.stream()
				.filter(p -> "credit".equals(p.paymentMethod()))
				.map(PaymentMixAggregate::total)
				.findFirst().orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
		);
	}
}
