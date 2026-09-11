package com.example.waterrefillapijava;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;

class OrderControllerTest extends AbstractIntegrationTest {

	@Test
	void listOrdersReturnsAll() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(get("/api/v1/orders")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.current_page").value(1))
			.andExpect(jsonPath("$.per_page").value(10))
			.andExpect(jsonPath("$.total_items").isNumber())
			.andExpect(jsonPath("$.total_pages").isNumber());
	}

	@Test
	void createWalkInOrderWithItemsReturnsOrder() throws Exception {
		final String token = loginAsTestUser();

		final String productResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Order Test Product",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 50,
					"reorder_point", 5
				))))
			.andReturn().getResponse().getContentAsString();
		final Long productId = objectMapper.readTree(productResult).get("id").asLong();

		mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"order_type", "walk_in",
					"payment_method", "cash",
					"total_amount", 30.0,
					"amount_paid", 50.0,
					"items", java.util.List.of(
						Map.of("product_id", productId, "quantity", 3, "unit_price", 10.0)
					)
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.order_type").value("walk_in"))
			.andExpect(jsonPath("$.status").value("queued"))
			.andExpect(jsonPath("$.payment_method").value("cash"))
			.andExpect(jsonPath("$.change_returned").value(20.0))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].product_name").value("Order Test Product"))
			.andExpect(jsonPath("$.items[0].quantity").value(3))
			.andExpect(jsonPath("$.items[0].subtotal").value(30.0));
	}

	@Test
	void createDeliveryOrderWithAddressReturnsOrder() throws Exception {
		final String token = loginAsTestUser();

		final String productResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Delivery Test Product",
					"type", "accessory",
					"price", 25.0,
					"stock_quantity", 50,
					"reorder_point", 5
				))))
			.andReturn().getResponse().getContentAsString();
		final Long productId = objectMapper.readTree(productResult).get("id").asLong();

		mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"order_type", "delivery",
					"payment_method", "cash",
					"delivery_fee", 20.0,
					"delivery_address", "123 Water St",
					"items", java.util.List.of(
						Map.of("product_id", productId, "quantity", 2, "unit_price", 25.0)
					)
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.order_type").value("delivery"))
			.andExpect(jsonPath("$.delivery_status").value("pending"))
			.andExpect(jsonPath("$.delivery_address").value("123 Water St"));
	}

	@Test
	void createDeliveryOrderWithoutAddressReturns422() throws Exception {
		final String token = loginAsTestUser();

		final String productResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "No Address Product",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 50,
					"reorder_point", 5
				))))
			.andReturn().getResponse().getContentAsString();
		final Long productId = objectMapper.readTree(productResult).get("id").asLong();

		mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"order_type", "delivery",
					"payment_method", "cash",
					"items", java.util.List.of(
						Map.of("product_id", productId, "quantity", 1, "unit_price", 10.0)
					)
				))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors.delivery_address").isArray());
	}

	@Test
	void createOrderWithEmptyItemsReturns422() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"order_type", "walk_in",
					"payment_method", "cash",
					"items", java.util.List.of()
				))))
			.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void createOrderDeductsStock() throws Exception {
		final String token = loginAsTestUser();

		final String productResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Stock Test Product",
					"type", "accessory",
					"price", 5.0,
					"stock_quantity", 20,
					"reorder_point", 2
				))))
			.andReturn().getResponse().getContentAsString();
		final Long productId = objectMapper.readTree(productResult).get("id").asLong();

		mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"order_type", "walk_in",
					"payment_method", "cash",
					"items", java.util.List.of(
						Map.of("product_id", productId, "quantity", 5, "unit_price", 5.0)
					)
				))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/products/" + productId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stock_quantity").value(15));
	}

	@Test
	void advanceWalkInOrderStatusThroughValidTransitions() throws Exception {
		final String token = loginAsTestUser();

		final String productResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Status Test Product",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 50,
					"reorder_point", 5
				))))
			.andReturn().getResponse().getContentAsString();
		final Long productId = objectMapper.readTree(productResult).get("id").asLong();

		final String orderResult = mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"order_type", "walk_in",
					"payment_method", "cash",
					"items", java.util.List.of(
						Map.of("product_id", productId, "quantity", 1, "unit_price", 10.0)
					)
				))))
			.andReturn().getResponse().getContentAsString();
		final Long orderId = objectMapper.readTree(orderResult).get("id").asLong();

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "processing"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("processing"));

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "completed"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("completed"));
	}

	@Test
	void advanceOrderStatusWithInvalidTransitionReturns422() throws Exception {
		final String token = loginAsTestUser();

		final String productResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Invalid Transition Product",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 50,
					"reorder_point", 5
				))))
			.andReturn().getResponse().getContentAsString();
		final Long productId = objectMapper.readTree(productResult).get("id").asLong();

		final String orderResult = mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"order_type", "walk_in",
					"payment_method", "cash",
					"items", java.util.List.of(
						Map.of("product_id", productId, "quantity", 1, "unit_price", 10.0)
					)
				))))
			.andReturn().getResponse().getContentAsString();
		final Long orderId = objectMapper.readTree(orderResult).get("id").asLong();

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "completed"))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors.status").isArray());
	}

	@Test
	void deleteOrderRestoresStock() throws Exception {
		final String token = loginAsTestUser();

		final String productResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Delete Stock Product",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 20,
					"reorder_point", 2
				))))
			.andReturn().getResponse().getContentAsString();
		final Long productId = objectMapper.readTree(productResult).get("id").asLong();

		final String orderResult = mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"order_type", "walk_in",
					"payment_method", "cash",
					"items", java.util.List.of(
						Map.of("product_id", productId, "quantity", 5, "unit_price", 10.0)
					)
				))))
			.andReturn().getResponse().getContentAsString();
		final Long orderId = objectMapper.readTree(orderResult).get("id").asLong();

		mockMvc.perform(get("/api/v1/products/" + productId)
				.header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.stock_quantity").value(15));

		mockMvc.perform(delete("/api/v1/orders/" + orderId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/products/" + productId)
				.header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.stock_quantity").value(20));
	}

	@Test
	void getOrderByIdReturnsOrder() throws Exception {
		final String token = loginAsTestUser();

		final String productResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Get Order Product",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 50,
					"reorder_point", 5
				))))
			.andReturn().getResponse().getContentAsString();
		final Long productId = objectMapper.readTree(productResult).get("id").asLong();

		final String orderResult = mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"order_type", "walk_in",
					"payment_method", "cash",
					"items", java.util.List.of(
						Map.of("product_id", productId, "quantity", 2, "unit_price", 10.0)
					)
				))))
			.andReturn().getResponse().getContentAsString();
		final Long orderId = objectMapper.readTree(orderResult).get("id").asLong();

		mockMvc.perform(get("/api/v1/orders/" + orderId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(orderId))
			.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void recordDeliveryUpdatesOrder() throws Exception {
		final String token = loginAsTestUser();

		final String productResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Delivery Record Product",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 50,
					"reorder_point", 5
				))))
			.andReturn().getResponse().getContentAsString();
		final Long productId = objectMapper.readTree(productResult).get("id").asLong();

		final String orderResult = mockMvc.perform(post("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"order_type", "delivery",
					"payment_method", "cash",
					"delivery_address", "456 Delivery St",
					"items", java.util.List.of(
						Map.of("product_id", productId, "quantity", 1, "unit_price", 10.0)
					)
				))))
			.andReturn().getResponse().getContentAsString();
		final Long orderId = objectMapper.readTree(orderResult).get("id").asLong();

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/delivery")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"delivery_status", "delivered",
					"bottles_returned", 0,
					"cash_collected", 10.0
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.delivery_status").value("delivered"))
			.andExpect(jsonPath("$.status").value("completed"));
	}

	@Test
	void listOrdersWithPaginationReturnsCorrectPage() throws Exception {
		final String token = loginAsTestUser();

		final String productResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Pagination Product",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 50,
					"reorder_point", 5
				))))
			.andReturn().getResponse().getContentAsString();
		final Long productId = objectMapper.readTree(productResult).get("id").asLong();

		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/v1/orders")
					.header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"order_type", "walk_in",
						"payment_method", "cash",
						"items", java.util.List.of(
							Map.of("product_id", productId, "quantity", 1, "unit_price", 10.0)
						)
					))))
				.andExpect(status().isOk());
		}

		mockMvc.perform(get("/api/v1/orders")
				.header("Authorization", "Bearer " + token)
				.param("page", "1")
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.current_page").value(1))
			.andExpect(jsonPath("$.per_page").value(2));
	}
}
