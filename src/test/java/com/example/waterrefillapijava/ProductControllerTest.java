package com.example.waterrefillapijava;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

class ProductControllerTest extends AbstractIntegrationTest {

	@Test
	void listProductsReturnsAll() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(get("/api/v1/products")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.current_page").value(1))
			.andExpect(jsonPath("$.per_page").value(10))
			.andExpect(jsonPath("$.total_items").isNumber())
			.andExpect(jsonPath("$.total_pages").isNumber());
	}

	@Test
	void createProductReturnsProduct() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Test Product",
					"type", "accessory",
					"price", 25.0,
					"stock_quantity", 50,
					"reorder_point", 10
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Test Product"))
			.andExpect(jsonPath("$.type").value("accessory"))
			.andExpect(jsonPath("$.price").value(25.0))
			.andExpect(jsonPath("$.stock_quantity").value(50))
			.andExpect(jsonPath("$.reorder_point").value(10));
	}

	@Test
	void createWaterRefillProductWithVolumeReturnsProduct() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Refill Product",
					"type", "water_refill",
					"volume_gallons", 5,
					"price", 25.0,
					"stock_quantity", 100,
					"reorder_point", 20
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Refill Product"))
			.andExpect(jsonPath("$.type").value("water_refill"))
			.andExpect(jsonPath("$.volume_gallons").value(5));
	}

	@Test
	void createWaterRefillWithoutVolumeReturns422() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Refill No Volume",
					"type", "water_refill",
					"price", 25.0,
					"stock_quantity", 100,
					"reorder_point", 20
				))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors.volume_gallons").isArray());
	}

	@Test
	void createProductWithDuplicateNameReturns409() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Dup Name",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 5,
					"reorder_point", 1
				))))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "dup name",
					"type", "accessory",
					"price", 20.0,
					"stock_quantity", 10,
					"reorder_point", 2
				))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void createProductWithMissingFieldsReturns422() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors").isMap());
	}

	@Test
	void getProductByIdReturnsProduct() throws Exception {
		final String token = loginAsTestUser();

		final String createResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Get By ID",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 5,
					"reorder_point", 1
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResult).get("id").asLong();

		mockMvc.perform(get("/api/v1/products/" + id)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Get By ID"));
	}

	@Test
	void updateProductReturnsUpdated() throws Exception {
		final String token = loginAsTestUser();

		final String createResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Update Me",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 5,
					"reorder_point", 1
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResult).get("id").asLong();

		mockMvc.perform(put("/api/v1/products/" + id)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Updated Name",
					"type", "accessory",
					"price", 20.0,
					"stock_quantity", 10,
					"reorder_point", 2
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Updated Name"))
			.andExpect(jsonPath("$.price").value(20.0));
	}

	@Test
	void updateProductWithDuplicateNameReturns409() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Existing Name",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 5,
					"reorder_point", 1
				))))
			.andExpect(status().isOk());

		final String createResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Another Product",
					"type", "accessory",
					"price", 20.0,
					"stock_quantity", 10,
					"reorder_point", 2
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResult).get("id").asLong();

		mockMvc.perform(put("/api/v1/products/" + id)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Existing Name",
					"type", "accessory",
					"price", 20.0,
					"stock_quantity", 10,
					"reorder_point", 2
				))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void deleteProductReturnsSuccess() throws Exception {
		final String token = loginAsTestUser();

		final String createResult = mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Delete Me",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 5,
					"reorder_point", 1
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResult).get("id").asLong();

		mockMvc.perform(delete("/api/v1/products/" + id)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Product deleted successfully."));
	}

	@Test
	void getProductWithoutAuthReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/products"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void listProductsWithPaginationReturnsCorrectPage() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(get("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.param("page", "1")
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.current_page").value(1))
			.andExpect(jsonPath("$.per_page").value(2))
			.andExpect(jsonPath("$.total_items").isNumber())
			.andExpect(jsonPath("$.total_pages").isNumber());
	}

	@Test
	void listProductsWithSearchReturnsFilteredResults() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Unique Product XYZ",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 5,
					"reorder_point", 1
				))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.param("search", "Unique Product XYZ"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data[0].name").value("Unique Product XYZ"));
	}
}
