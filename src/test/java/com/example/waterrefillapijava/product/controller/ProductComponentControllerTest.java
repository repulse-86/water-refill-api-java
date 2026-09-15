package com.example.waterrefillapijava.product.controller;
import com.example.waterrefillapijava.shared.AbstractIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class ProductComponentControllerTest extends AbstractIntegrationTest {

	@Test
	void createProductWithComponentsReturnsProductAndComponents() throws Exception {
		final String token = loginAsTestUser();

		final Long componentAId = extractId(createMultipartProduct(token, Map.of(
			"name", "Component A",
			"type", "accessory",
			"price", 2.0,
			"stock_quantity", 100,
			"reorder_point", 10
		)));

		final Long componentBId = extractId(createMultipartProduct(token, Map.of(
			"name", "Component B",
			"type", "accessory",
			"price", 1.0,
			"stock_quantity", 100,
			"reorder_point", 10
		)));

		final String productResult = mockMvc.perform(multipart("/api/v1/products")
				.file(new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(Map.of(
					"name", "Water With BOM",
					"type", "water_refill",
					"volume_gallons", 5,
					"price", 25.0,
					"stock_quantity", 50,
					"reorder_point", 10,
					"components", java.util.List.of(
						Map.of("component_id", componentAId, "quantity", 2),
						Map.of("component_id", componentBId, "quantity", 1)
					)
				))))
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Water With BOM"))
			.andReturn().getResponse().getContentAsString();

		final Long productId = extractId(productResult);

		mockMvc.perform(get("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data.length()").value(2));
	}

	@Test
	void createProductWithSelfReferenceComponentReturns422() throws Exception {
		final String token = loginAsTestUser();

		final Long productId = extractId(createMultipartProduct(token, Map.of(
			"name", "Self Ref Product",
			"type", "accessory",
			"price", 10.0,
			"stock_quantity", 5,
			"reorder_point", 1
		)));

		mockMvc.perform(multipart("/api/v1/products/" + productId)
				.file(new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(Map.of(
					"name", "Self Ref Product",
					"type", "accessory",
					"price", 10.0,
					"stock_quantity", 5,
					"reorder_point", 1,
					"components", java.util.List.of(
						Map.of("component_id", productId, "quantity", 1)
					)
				))))
				.with(req -> { req.setMethod("PUT"); return req; })
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void updateProductReplacesComponents() throws Exception {
		final String token = loginAsTestUser();

		final Long compId = extractId(createMultipartProduct(token, Map.of(
			"name", "Comp To Replace",
			"type", "accessory",
			"price", 3.0,
			"stock_quantity", 100,
			"reorder_point", 10
		)));

		final Long productId = extractId(createMultipartProduct(token, Map.of(
			"name", "Product With Old BOM",
			"type", "water_refill",
			"volume_gallons", 5,
			"price", 25.0,
			"stock_quantity", 50,
			"reorder_point", 10,
			"components", java.util.List.of(
				Map.of("component_id", compId, "quantity", 1)
			)
		)));

		mockMvc.perform(get("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1));

		final Long comp2Id = extractId(createMultipartProduct(token, Map.of(
			"name", "New Comp",
			"type", "accessory",
			"price", 5.0,
			"stock_quantity", 100,
			"reorder_point", 10
		)));

		mockMvc.perform(multipart("/api/v1/products/" + productId)
				.file(new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(Map.of(
					"name", "Product With Old BOM",
					"type", "water_refill",
					"volume_gallons", 5,
					"price", 25.0,
					"stock_quantity", 50,
					"reorder_point", 10,
					"components", java.util.List.of(
						Map.of("component_id", comp2Id, "quantity", 3)
					)
				))))
				.with(req -> { req.setMethod("PUT"); return req; })
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].component_id").value(comp2Id))
			.andExpect(jsonPath("$.data[0].quantity").value(3));
	}

	@Test
	void listProductComponentsReturnsPage() throws Exception {
		final String token = loginAsTestUser();

		final Long productId = extractId(createMultipartProduct(token, Map.of(
			"name", "Parent Product",
			"type", "accessory",
			"price", 10.0,
			"stock_quantity", 5,
			"reorder_point", 1
		)));

		final Long componentId = extractId(createMultipartProduct(token, Map.of(
			"name", "Child Component",
			"type", "accessory",
			"price", 5.0,
			"stock_quantity", 10,
			"reorder_point", 1
		)));

		mockMvc.perform(post("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", componentId,
					"quantity", 2
				))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.current_page").value(1))
			.andExpect(jsonPath("$.per_page").value(10))
			.andExpect(jsonPath("$.total_items").isNumber())
			.andExpect(jsonPath("$.total_pages").isNumber());
	}

	@Test
	void addProductComponentReturnsComponent() throws Exception {
		final String token = loginAsTestUser();

		final Long productId = extractId(createMultipartProduct(token, Map.of(
			"name", "Add Comp Parent",
			"type", "accessory",
			"price", 10.0,
			"stock_quantity", 5,
			"reorder_point", 1
		)));

		final Long componentId = extractId(createMultipartProduct(token, Map.of(
			"name", "Add Comp Child",
			"type", "accessory",
			"price", 5.0,
			"stock_quantity", 10,
			"reorder_point", 1
		)));

		mockMvc.perform(post("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", componentId,
					"quantity", 3
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.product_id").value(productId))
			.andExpect(jsonPath("$.component_id").value(componentId))
			.andExpect(jsonPath("$.component_name").value("Add Comp Child"))
			.andExpect(jsonPath("$.quantity").value(3));
	}

	@Test
	void addComponentToMissingProductReturns404() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/products/999999/components")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", 1,
					"quantity", 1
				))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void addComponentWithDuplicateReturns409() throws Exception {
		final String token = loginAsTestUser();

		final Long productId = extractId(createMultipartProduct(token, Map.of(
			"name", "Dup Comp Parent",
			"type", "accessory",
			"price", 10.0,
			"stock_quantity", 5,
			"reorder_point", 1
		)));

		final Long componentId = extractId(createMultipartProduct(token, Map.of(
			"name", "Dup Comp Child",
			"type", "accessory",
			"price", 5.0,
			"stock_quantity", 10,
			"reorder_point", 1
		)));

		mockMvc.perform(post("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", componentId,
					"quantity", 1
				))))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", componentId,
					"quantity", 2
				))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void addSelfReferenceComponentReturns422() throws Exception {
		final String token = loginAsTestUser();

		final Long productId = extractId(createMultipartProduct(token, Map.of(
			"name", "Self Ref Product",
			"type", "accessory",
			"price", 10.0,
			"stock_quantity", 5,
			"reorder_point", 1
		)));

		mockMvc.perform(post("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", productId,
					"quantity", 1
				))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors.component_id").isArray());
	}

	@Test
	void addComponentWithZeroQuantityReturns422() throws Exception {
		final String token = loginAsTestUser();

		final Long productId = extractId(createMultipartProduct(token, Map.of(
			"name", "Zero Qty Parent",
			"type", "accessory",
			"price", 10.0,
			"stock_quantity", 5,
			"reorder_point", 1
		)));

		final Long componentId = extractId(createMultipartProduct(token, Map.of(
			"name", "Zero Qty Child",
			"type", "accessory",
			"price", 5.0,
			"stock_quantity", 10,
			"reorder_point", 1
		)));

		mockMvc.perform(post("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", componentId,
					"quantity", 0
				))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void updateProductComponentQuantityReturnsUpdated() throws Exception {
		final String token = loginAsTestUser();

		final Long productId = extractId(createMultipartProduct(token, Map.of(
			"name", "Update Comp Parent",
			"type", "accessory",
			"price", 10.0,
			"stock_quantity", 5,
			"reorder_point", 1
		)));

		final Long componentId = extractId(createMultipartProduct(token, Map.of(
			"name", "Update Comp Child",
			"type", "accessory",
			"price", 5.0,
			"stock_quantity", 10,
			"reorder_point", 1
		)));

		mockMvc.perform(post("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", componentId,
					"quantity", 1
				))))
			.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/products/" + productId + "/components/" + componentId)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", componentId,
					"quantity", 5
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quantity").value(5));
	}

	@Test
	void deleteProductComponentReturnsSuccess() throws Exception {
		final String token = loginAsTestUser();

		final Long productId = extractId(createMultipartProduct(token, Map.of(
			"name", "Delete Comp Parent",
			"type", "accessory",
			"price", 10.0,
			"stock_quantity", 5,
			"reorder_point", 1
		)));

		final Long componentId = extractId(createMultipartProduct(token, Map.of(
			"name", "Delete Comp Child",
			"type", "accessory",
			"price", 5.0,
			"stock_quantity", 10,
			"reorder_point", 1
		)));

		mockMvc.perform(post("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", componentId,
					"quantity", 1
				))))
			.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/products/" + productId + "/components/" + componentId)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Product component deleted successfully."));
	}

	@Test
	void listProductComponentsWithoutAuthReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/products/1/components"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void listProductComponentsWithPaginationReturnsCorrectPage() throws Exception {
		final String token = loginAsTestUser();

		final Long productId = extractId(createMultipartProduct(token, Map.of(
			"name", "Paginated Comp Parent",
			"type", "accessory",
			"price", 10.0,
			"stock_quantity", 5,
			"reorder_point", 1
		)));

		final Long componentId = extractId(createMultipartProduct(token, Map.of(
			"name", "Paginated Comp Child",
			"type", "accessory",
			"price", 5.0,
			"stock_quantity", 10,
			"reorder_point", 1
		)));

		mockMvc.perform(post("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", componentId,
					"quantity", 1
				))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.param("page", "1")
				.param("size", "5"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.current_page").value(1))
			.andExpect(jsonPath("$.per_page").value(5));
	}

	@Test
	void listProductComponentsWithSearchReturnsFilteredResults() throws Exception {
		final String token = loginAsTestUser();

		final Long productId = extractId(createMultipartProduct(token, Map.of(
			"name", "Search Comp Parent",
			"type", "accessory",
			"price", 10.0,
			"stock_quantity", 5,
			"reorder_point", 1
		)));

		final Long componentId = extractId(createMultipartProduct(token, Map.of(
			"name", "Unique XYZ Component",
			"type", "accessory",
			"price", 5.0,
			"stock_quantity", 10,
			"reorder_point", 1
		)));

		mockMvc.perform(post("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"component_id", componentId,
					"quantity", 1
				))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/products/" + productId + "/components")
				.header("Authorization", "Bearer " + token)
				.param("search", "Unique XYZ"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data[0].component_name").value("Unique XYZ Component"));
	}
}
