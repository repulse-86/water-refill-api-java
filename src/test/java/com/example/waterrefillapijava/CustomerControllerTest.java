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

class CustomerControllerTest extends AbstractIntegrationTest {

	@Test
	void listCustomersReturnsAll() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(get("/api/v1/customers")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.current_page").value(1))
			.andExpect(jsonPath("$.per_page").value(10))
			.andExpect(jsonPath("$.total_items").isNumber())
			.andExpect(jsonPath("$.total_pages").isNumber());
	}

	@Test
	void createCustomerReturnsCustomer() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Test Customer",
					"phone", "09123456789",
					"email", "test@example.com"
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Test Customer"))
			.andExpect(jsonPath("$.phone").value("09123456789"))
			.andExpect(jsonPath("$.email").value("test@example.com"))
			.andExpect(jsonPath("$.subscriber_status").value("active"))
			.andExpect(jsonPath("$.bottle_debt").value(0));
	}

	@Test
	void createCustomerWithDuplicatePhoneReturns409() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Dup Phone",
					"phone", "09123456789",
					"email", "dup@example.com"
				))))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Dup Phone 2",
					"phone", "09123456789",
					"email", "dup2@example.com"
				))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void createCustomerWithDuplicateEmailReturns409() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Dup Email",
					"phone", "09123456794",
					"email", "dup-email@example.com"
				))))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Dup Email 2",
					"phone", "09123456795",
					"email", "dup-email@example.com"
				))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void getCustomerByIdReturnsCustomer() throws Exception {
		final String token = loginAsTestUser();

		final String createResult = mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Get By ID",
					"phone", "09123456790",
					"email", "getbyid@example.com"
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResult).get("id").asLong();

		mockMvc.perform(get("/api/v1/customers/" + id)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Get By ID"));
	}

	@Test
	void updateCustomerReturnsUpdated() throws Exception {
		final String token = loginAsTestUser();

		final String createResult = mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Update Me",
					"phone", "09123456791",
					"email", "update@example.com"
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResult).get("id").asLong();

		mockMvc.perform(put("/api/v1/customers/" + id)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Updated Name",
					"phone", "09123456791",
					"email", "updated@example.com",
					"subscriber_status", "inactive",
					"bottle_debt", 0,
					"outstanding_balance", 0.0
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Updated Name"))
			.andExpect(jsonPath("$.subscriber_status").value("inactive"));
	}

	@Test
	void updateCustomerWithDuplicateEmailReturns409() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Email Owner",
					"phone", "09123456796",
					"email", "owner@example.com"
				))))
			.andExpect(status().isOk());

		final String createResult = mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Email Updater",
					"phone", "09123456797",
					"email", "updater@example.com"
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResult).get("id").asLong();

		mockMvc.perform(put("/api/v1/customers/" + id)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Email Updater",
					"phone", "09123456797",
					"email", "owner@example.com",
					"subscriber_status", "active",
					"bottle_debt", 0,
					"outstanding_balance", 0.0
				))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void deleteCustomerReturnsSuccess() throws Exception {
		final String token = loginAsTestUser();

		final String createResult = mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Delete Me",
					"phone", "09123456792",
					"email", "delete@example.com"
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResult).get("id").asLong();

		mockMvc.perform(delete("/api/v1/customers/" + id)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Customer deleted successfully."));
	}

	@Test
	void settleCustomerReducesDebtAndBalance() throws Exception {
		final String token = loginAsTestUser();

		final String createResult = mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Settle Me",
					"phone", "09123456793",
					"email", "settle@example.com"
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResult).get("id").asLong();

		mockMvc.perform(put("/api/v1/customers/" + id)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Settle Me",
					"phone", "09123456793",
					"email", "settle@example.com",
					"subscriber_status", "active",
					"bottle_debt", 0,
					"outstanding_balance", 0.0
				))))
			.andExpect(status().isOk());

		customerRepository.findById(id).ifPresent(c -> {
			c.setBottleDebt(5);
			c.setOutstandingBalance(new java.math.BigDecimal("200"));
			customerRepository.save(c);
		});

		mockMvc.perform(post("/api/v1/customers/" + id + "/settle")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"bottle_return", 2,
					"cash_payment", 50.0
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bottle_debt").value(3))
			.andExpect(jsonPath("$.outstanding_balance").value(150.0));
	}
}
