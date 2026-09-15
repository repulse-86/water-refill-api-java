package com.example.waterrefillapijava.setting.controller;
import com.example.waterrefillapijava.shared.AbstractIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

class SettingControllerTest extends AbstractIntegrationTest {

	@Test
	void getSettingsReturnsDefaults() throws Exception {
		settingRepository.deleteById(1L);
		final String token = loginAsTestUser();

		mockMvc.perform(get("/api/v1/settings")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.store_name").value("My Water Refilling Station"))
			.andExpect(jsonPath("$.currency").value("PHP"))
			.andExpect(jsonPath("$.low_stock_threshold").value(10));
	}

	@Test
	void updateSettingsReturnsUpdatedValues() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(put("/api/v1/settings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"store_name", "Aqua Pure Station",
					"store_address", "123 Water St",
					"store_phone", "09171234567",
					"currency", "PHP",
					"low_stock_threshold", 20
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.store_name").value("Aqua Pure Station"))
			.andExpect(jsonPath("$.store_address").value("123 Water St"))
			.andExpect(jsonPath("$.store_phone").value("09171234567"))
			.andExpect(jsonPath("$.low_stock_threshold").value(20));
	}

	@Test
	void getSettingsWithoutAuthReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/settings"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void updateSettingsWithMissingFieldsReturns422() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(put("/api/v1/settings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}
}
