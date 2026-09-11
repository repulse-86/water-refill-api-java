package com.example.waterrefillapijava;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class DashboardControllerTest extends AbstractIntegrationTest {

	@Test
	void getDashboardReturnsAllSections() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(get("/api/v1/dashboard")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.today.date").isString())
			.andExpect(jsonPath("$.today.revenue").isNumber())
			.andExpect(jsonPath("$.today.order_count").isNumber())
			.andExpect(jsonPath("$.today.cash").isNumber())
			.andExpect(jsonPath("$.today.e_wallet").isNumber())
			.andExpect(jsonPath("$.today.credit").isNumber())
			.andExpect(jsonPath("$.today.gallons").isNumber())
			.andExpect(jsonPath("$.quick_stats.gallons_pumped").isNumber())
			.andExpect(jsonPath("$.quick_stats.bottles_returned").isNumber())
			.andExpect(jsonPath("$.quick_stats.active_customers").isNumber())
			.andExpect(jsonPath("$.quick_stats.pending_orders").isNumber())
			.andExpect(jsonPath("$.pending_orders").isArray())
			.andExpect(jsonPath("$.low_stock").isArray())
			.andExpect(jsonPath("$.sales_trend").isArray())
			.andExpect(jsonPath("$.top_products").isArray())
			.andExpect(jsonPath("$.payment_mix").isArray())
			.andExpect(jsonPath("$.payment_mix[0].key").value("cash"))
			.andExpect(jsonPath("$.payment_mix[1].key").value("e_wallet"))
			.andExpect(jsonPath("$.payment_mix[2].key").value("credit"));
	}

	@Test
	void getDashboardWithNoDataReturnsEmptyArrays() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(get("/api/v1/dashboard")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pending_orders").isArray())
			.andExpect(jsonPath("$.low_stock").isArray())
			.andExpect(jsonPath("$.sales_trend").isArray())
			.andExpect(jsonPath("$.top_products").isArray())
			.andExpect(jsonPath("$.payment_mix").isArray());
	}
}
