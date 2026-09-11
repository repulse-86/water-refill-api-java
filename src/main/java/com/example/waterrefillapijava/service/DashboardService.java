package com.example.waterrefillapijava.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.example.waterrefillapijava.model.MeterReading;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderItem;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.PaymentMethod;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductType;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.MeterReadingRepository;
import com.example.waterrefillapijava.repository.OrderItemRepository;
import com.example.waterrefillapijava.repository.OrderRepository;
import com.example.waterrefillapijava.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;
	private final CustomerRepository customerRepository;
	private final MeterReadingRepository meterReadingRepository;

	@Transactional(readOnly = true)
	public DashboardResponse getDashboard() {
		final LocalDate today = LocalDate.now();

		final List<Order> completedOrders = orderRepository.findByStatus(OrderStatus.completed, Pageable.unpaged()).getContent();
		final List<Order> pendingOrdersList = orderRepository.findByStatusNotOrderByCreatedAtDesc(OrderStatus.completed);
		final List<Product> lowStockProducts = productRepository.findLowStock();
		final List<Product> products = productRepository.findAll();
		final List<MeterReading> readings = meterReadingRepository.findAll();

		final TodaySummary todaySummary = computeTodaySales(completedOrders, today);
		final int activeCustomerCount = (int) customerRepository.countBySubscriberStatus("active");
		final int pendingOrderCount = (int) orderRepository.countByStatusNot(OrderStatus.completed);
		final int bottlesReturned = orderRepository.sumBottlesReturned();
		final QuickStats quickStats = computeQuickStats(readings, completedOrders, products, bottlesReturned, activeCustomerCount, pendingOrderCount, today);
		final List<PendingOrder> pendingOrders = computePendingOrders(pendingOrdersList);
		final List<LowStockProduct> lowStock = computeLowStock(lowStockProducts);
		final List<DailySalesRow> salesTrend = computeDailySales(completedOrders, products).stream()
			.sorted((a, b) -> a.date().compareTo(b.date()))
			.toList();
		final int trendLimit = Math.min(salesTrend.size(), 7);
		final List<DailySalesRow> lastSeven = salesTrend.subList(Math.max(0, salesTrend.size() - trendLimit), salesTrend.size());
		final List<TopProduct> topProducts = computeProductPerformance(completedOrders, products).stream()
			.limit(5)
			.toList();
		final List<PaymentMix> paymentMix = computePaymentMix(completedOrders);

		return new DashboardResponse(
			todaySummary,
			quickStats,
			pendingOrders,
			lowStock,
			lastSeven,
			topProducts,
			paymentMix
		);
	}

	private TodaySummary computeTodaySales(List<Order> completedOrders, LocalDate today) {
		BigDecimal revenue = BigDecimal.ZERO;
		BigDecimal cash = BigDecimal.ZERO;
		BigDecimal eWallet = BigDecimal.ZERO;
		BigDecimal credit = BigDecimal.ZERO;
		int orderCount = 0;
		BigDecimal gallons = BigDecimal.ZERO;

		final Map<Long, Product> productMap = new HashMap<>();
		for (Order order : completedOrders) {
			if (order.getCreatedAt() != null && order.getCreatedAt().toLocalDate().equals(today)) {
				orderCount++;
				revenue = revenue.add(order.getTotalAmount());
				switch (order.getPaymentMethod()) {
					case cash -> cash = cash.add(order.getTotalAmount());
					case e_wallet -> eWallet = eWallet.add(order.getTotalAmount());
					case credit -> credit = credit.add(order.getTotalAmount());
				}
				for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
					final Product product = productMap.computeIfAbsent(item.getProduct().getId(), id -> item.getProduct());
					if (product.getType() == ProductType.water_refill && product.getVolumeGallons() != null) {
						gallons = gallons.add(product.getVolumeGallons().multiply(BigDecimal.valueOf(item.getQuantity())));
					}
				}
			}
		}

		return new TodaySummary(
			today.toString(),
			revenue.setScale(2, RoundingMode.HALF_UP),
			orderCount,
			cash.setScale(2, RoundingMode.HALF_UP),
			eWallet.setScale(2, RoundingMode.HALF_UP),
			credit.setScale(2, RoundingMode.HALF_UP),
			gallons.setScale(2, RoundingMode.HALF_UP)
		);
	}

	private QuickStats computeQuickStats(List<MeterReading> readings, List<Order> completedOrders,
			List<Product> products, int bottlesReturned, int activeCustomers, int pendingOrderCount, LocalDate today) {

		final BigDecimal gallonsPumped = computeGallonsPumped(readings, completedOrders, products, today);

		return new QuickStats(gallonsPumped, bottlesReturned, activeCustomers, pendingOrderCount);
	}

	private BigDecimal computeGallonsPumped(List<MeterReading> readings, List<Order> completedOrders,
			List<Product> products, LocalDate today) {
		final List<MeterReading> sorted = new ArrayList<>(readings);
		sorted.sort((a, b) -> a.getReadingDate().compareTo(b.getReadingDate()));

		if (sorted.size() >= 2) {
			final MeterReading latest = sorted.get(sorted.size() - 1);
			final MeterReading previous = sorted.get(sorted.size() - 2);
			return latest.getMeterValue().subtract(previous.getMeterValue()).setScale(2, RoundingMode.HALF_UP);
		}

		final Map<Long, Product> productMap = new HashMap<>();
		BigDecimal total = BigDecimal.ZERO;
		for (Order order : completedOrders) {
			if (order.getCreatedAt() != null
					&& order.getCreatedAt().toLocalDate().equals(today)) {
				for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
					final Product product = productMap.computeIfAbsent(item.getProduct().getId(), id -> item.getProduct());
					if (product.getType() == ProductType.water_refill && product.getVolumeGallons() != null) {
						total = total.add(product.getVolumeGallons().multiply(BigDecimal.valueOf(item.getQuantity())));
					}
				}
			}
		}
		return total.setScale(2, RoundingMode.HALF_UP);
	}

	private List<PendingOrder> computePendingOrders(List<Order> pendingOrders) {
		return pendingOrders.stream()
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

	private List<LowStockProduct> computeLowStock(List<Product> products) {
		return products.stream()
			.filter(p -> p.getStockQuantity() <= p.getReorderPoint())
			.sorted((a, b) -> Integer.compare(a.getStockQuantity(), b.getStockQuantity()))
			.map(p -> new LowStockProduct(
				p.getId(),
				p.getName(),
				p.getType().name(),
				p.getStockQuantity(),
				p.getReorderPoint()
			))
			.toList();
	}

	private List<DailySalesRow> computeDailySales(List<Order> completedOrders, List<Product> products) {
		final Map<Long, Product> productMap = new HashMap<>();
		products.forEach(p -> productMap.put(p.getId(), p));

		final Map<String, SalesAccumulator> byDay = new HashMap<>();

		for (Order order : completedOrders) {
			if (order.getCreatedAt() == null) continue;
			final String date = order.getCreatedAt().toLocalDate().toString();
			final SalesAccumulator row = byDay.computeIfAbsent(date, SalesAccumulator::new);

			row.orderCount++;
			row.revenue = row.revenue.add(order.getTotalAmount());
			switch (order.getPaymentMethod()) {
				case cash -> row.cash = row.cash.add(order.getTotalAmount());
				case e_wallet -> row.eWallet = row.eWallet.add(order.getTotalAmount());
				case credit -> row.credit = row.credit.add(order.getTotalAmount());
			}

			for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
				final Product product = productMap.get(item.getProduct().getId());
				if (product != null && product.getType() == ProductType.water_refill && product.getVolumeGallons() != null) {
					row.gallons = row.gallons.add(product.getVolumeGallons().multiply(BigDecimal.valueOf(item.getQuantity())));
				}
			}
		}

		return byDay.values().stream()
			.map(SalesAccumulator::toRow)
			.sorted((a, b) -> b.date().compareTo(a.date()))
			.toList();
	}

	private static class SalesAccumulator {
		String date;
		int orderCount;
		BigDecimal revenue = BigDecimal.ZERO;
		BigDecimal cash = BigDecimal.ZERO;
		BigDecimal eWallet = BigDecimal.ZERO;
		BigDecimal credit = BigDecimal.ZERO;
		BigDecimal gallons = BigDecimal.ZERO;

		SalesAccumulator(String date) { this.date = date; }

		DailySalesRow toRow() {
			return new DailySalesRow(
				date, date, orderCount,
				revenue.setScale(2, RoundingMode.HALF_UP),
				cash.setScale(2, RoundingMode.HALF_UP),
				eWallet.setScale(2, RoundingMode.HALF_UP),
				credit.setScale(2, RoundingMode.HALF_UP),
				gallons.setScale(2, RoundingMode.HALF_UP)
			);
		}
	}

	private List<TopProduct> computeProductPerformance(List<Order> completedOrders, List<Product> products) {
		final Map<Long, Product> productMap = new HashMap<>();
		products.forEach(p -> productMap.put(p.getId(), p));

		final record ProductAcc(String name, String type, int units, BigDecimal revenue) {
			ProductAcc add(int qty, BigDecimal sub) {
				return new ProductAcc(name, type, units + qty, revenue.add(sub));
			}
		}

		final Map<Long, ProductAcc> byId = new HashMap<>();
		for (Order order : completedOrders) {
			for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
				final Long pid = item.getProduct().getId();
				final Product product = productMap.get(pid);
				final String name = product != null ? product.getName() : item.getProduct().getName();
				final String type = product != null ? product.getType().name() : "unknown";
				byId.merge(pid, new ProductAcc(name, type, item.getQuantity(), item.getSubtotal()),
					(existing, incoming) -> existing.add(incoming.units, incoming.revenue));
			}
		}

		final BigDecimal totalRevenue = byId.values().stream()
			.map(ProductAcc::revenue)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		return byId.entrySet().stream()
			.sorted((a, b) -> b.getValue().revenue().compareTo(a.getValue().revenue()))
			.map(e -> {
				final ProductAcc acc = e.getValue();
				final BigDecimal sharePct = totalRevenue.compareTo(BigDecimal.ZERO) > 0
					? acc.revenue().multiply(new BigDecimal("100")).divide(totalRevenue, 1, RoundingMode.HALF_UP)
					: BigDecimal.ZERO;
				return new TopProduct(e.getKey(), acc.name(), acc.revenue(), acc.units(), sharePct);
			})
			.toList();
	}

	private List<PaymentMix> computePaymentMix(List<Order> completedOrders) {
		BigDecimal cash = BigDecimal.ZERO;
		BigDecimal eWallet = BigDecimal.ZERO;
		BigDecimal credit = BigDecimal.ZERO;

		for (Order order : completedOrders) {
			switch (order.getPaymentMethod()) {
				case cash -> cash = cash.add(order.getTotalAmount());
				case e_wallet -> eWallet = eWallet.add(order.getTotalAmount());
				case credit -> credit = credit.add(order.getTotalAmount());
			}
		}

		return List.of(
			new PaymentMix("cash", "Cash", cash.setScale(2, RoundingMode.HALF_UP)),
			new PaymentMix("e_wallet", "E-Wallet", eWallet.setScale(2, RoundingMode.HALF_UP)),
			new PaymentMix("credit", "Credit", credit.setScale(2, RoundingMode.HALF_UP))
		);
	}
}
