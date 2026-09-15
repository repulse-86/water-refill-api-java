package com.example.waterrefillapijava.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DashboardResponse(
	TodaySummary today,

	@JsonProperty("quick_stats")
	QuickStats quickStats,

	@JsonProperty("pending_orders")
	List<PendingOrder> pendingOrders,

	@JsonProperty("low_stock")
	List<LowStockProduct> lowStock,

	@JsonProperty("sales_trend")
	List<DailySalesRow> salesTrend,

	@JsonProperty("top_products")
	List<TopProduct> topProducts,

	@JsonProperty("payment_mix")
	List<PaymentMix> paymentMix
) {
	public record TodaySummary(
		String date,
		BigDecimal revenue,

		@JsonProperty("order_count")
		int orderCount,
		BigDecimal cash,

		@JsonProperty("e_wallet")
		BigDecimal eWallet,
		BigDecimal credit,
		BigDecimal gallons
	) {}

	public record QuickStats(
		@JsonProperty("gallons_pumped")
		BigDecimal gallonsPumped,

		@JsonProperty("bottles_returned")
		int bottlesReturned,

		@JsonProperty("active_customers")
		int activeCustomers,

		@JsonProperty("pending_orders")
		int pendingOrderCount
	) {}

	public record PendingOrder(
		Long id,

		@JsonProperty("customer_name")
		String customerName,

		@JsonProperty("order_type")
		String orderType,
		String status,

		@JsonProperty("total_amount")
		BigDecimal totalAmount,

		@JsonProperty("created_at")
		String createdAt
	) {}

	public record LowStockProduct(
		Long id,
		String name,
		String type,

		@JsonProperty("stock_quantity")
		int stockQuantity,

		@JsonProperty("reorder_point")
		int reorderPoint
	) {}

	public record DailySalesRow(
		String id,
		String date,

		@JsonProperty("order_count")
		int orderCount,
		BigDecimal revenue,
		BigDecimal cash,

		@JsonProperty("e_wallet")
		BigDecimal eWallet,
		BigDecimal credit,
		BigDecimal gallons
	) {}

	public record TopProduct(
		Long id,
		String name,
		BigDecimal revenue,
		int units,

		@JsonProperty("share_pct")
		BigDecimal sharePct
	) {}

	public record PaymentMix(
		String key,
		String name,
		BigDecimal value
	) {}
}
