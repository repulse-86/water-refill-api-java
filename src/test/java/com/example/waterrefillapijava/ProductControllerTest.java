package com.example.waterrefillapijava;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

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

		MockMultipartFile productPart = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Test Product",
				"type", "accessory",
				"price", 25.0,
				"stock_quantity", 50,
				"reorder_point", 10
			)));

		mockMvc.perform(multipart("/api/v1/products")
				.file(productPart)
				.header("Authorization", "Bearer " + token))
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

		MockMultipartFile productPart = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Refill Product",
				"type", "water_refill",
				"volume_gallons", 5,
				"price", 25.0,
				"stock_quantity", 100,
				"reorder_point", 20
			)));

		mockMvc.perform(multipart("/api/v1/products")
				.file(productPart)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Refill Product"))
			.andExpect(jsonPath("$.type").value("water_refill"))
			.andExpect(jsonPath("$.volume_gallons").value(5));
	}

	@Test
	void createWaterRefillWithoutVolumeReturns422() throws Exception {
		final String token = loginAsTestUser();

		MockMultipartFile productPart = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Refill No Volume",
				"type", "water_refill",
				"price", 25.0,
				"stock_quantity", 100,
				"reorder_point", 20
			)));

		mockMvc.perform(multipart("/api/v1/products")
				.file(productPart)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors.volume_gallons").isArray());
	}

	@Test
	void createProductWithDuplicateNameReturns409() throws Exception {
		final String token = loginAsTestUser();

		MockMultipartFile productPart1 = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Dup Name",
				"type", "accessory",
				"price", 10.0,
				"stock_quantity", 5,
				"reorder_point", 1
			)));

		mockMvc.perform(multipart("/api/v1/products")
				.file(productPart1)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		MockMultipartFile productPart2 = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "dup name",
				"type", "accessory",
				"price", 20.0,
				"stock_quantity", 10,
				"reorder_point", 2
			)));

		mockMvc.perform(multipart("/api/v1/products")
				.file(productPart2)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void createProductWithMissingFieldsReturns422() throws Exception {
		final String token = loginAsTestUser();

		MockMultipartFile productPart = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of()));

		mockMvc.perform(multipart("/api/v1/products")
				.file(productPart)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors").isMap());
	}

	@Test
	void getProductByIdReturnsProduct() throws Exception {
		final String token = loginAsTestUser();

		MockMultipartFile productPart = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Get By ID",
				"type", "accessory",
				"price", 10.0,
				"stock_quantity", 5,
				"reorder_point", 1
			)));

		final String createResult = mockMvc.perform(multipart("/api/v1/products")
				.file(productPart)
				.header("Authorization", "Bearer " + token))
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

		MockMultipartFile productPart1 = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Update Me",
				"type", "accessory",
				"price", 10.0,
				"stock_quantity", 5,
				"reorder_point", 1
			)));

		final String createResult = mockMvc.perform(multipart("/api/v1/products")
				.file(productPart1)
				.header("Authorization", "Bearer " + token))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResult).get("id").asLong();

		MockMultipartFile productPart2 = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Updated Name",
				"type", "accessory",
				"price", 20.0,
				"stock_quantity", 10,
				"reorder_point", 2
			)));

		mockMvc.perform(multipart("/api/v1/products/" + id)
				.file(productPart2)
				.with(request -> { request.setMethod("PUT"); return request; })
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Updated Name"))
			.andExpect(jsonPath("$.price").value(20.0));
	}

	@Test
	void updateProductWithDuplicateNameReturns409() throws Exception {
		final String token = loginAsTestUser();

		MockMultipartFile productPart1 = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Existing Name",
				"type", "accessory",
				"price", 10.0,
				"stock_quantity", 5,
				"reorder_point", 1
			)));

		mockMvc.perform(multipart("/api/v1/products")
				.file(productPart1)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		MockMultipartFile productPart2 = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Another Product",
				"type", "accessory",
				"price", 20.0,
				"stock_quantity", 10,
				"reorder_point", 2
			)));

		final String createResult = mockMvc.perform(multipart("/api/v1/products")
				.file(productPart2)
				.header("Authorization", "Bearer " + token))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResult).get("id").asLong();

		MockMultipartFile productPart3 = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Existing Name",
				"type", "accessory",
				"price", 20.0,
				"stock_quantity", 10,
				"reorder_point", 2
			)));

		mockMvc.perform(multipart("/api/v1/products/" + id)
				.file(productPart3)
				.with(request -> { request.setMethod("PUT"); return request; })
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void deleteProductReturnsSuccess() throws Exception {
		final String token = loginAsTestUser();

		MockMultipartFile productPart = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Delete Me",
				"type", "accessory",
				"price", 10.0,
				"stock_quantity", 5,
				"reorder_point", 1
			)));

		final String createResult = mockMvc.perform(multipart("/api/v1/products")
				.file(productPart)
				.header("Authorization", "Bearer " + token))
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

		MockMultipartFile productPart = new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(Map.of(
				"name", "Unique Product XYZ",
				"type", "accessory",
				"price", 10.0,
				"stock_quantity", 5,
				"reorder_point", 1
			)));

		mockMvc.perform(multipart("/api/v1/products")
				.file(productPart)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.param("search", "Unique Product XYZ"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data[0].name").value("Unique Product XYZ"));
	}
}
