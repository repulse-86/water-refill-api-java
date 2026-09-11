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

class MeterReadingControllerTest extends AbstractIntegrationTest {

	@Test
	void listMeterReadingsReturnsPaginatedResponse() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(get("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.total_items").exists())
			.andExpect(jsonPath("$.total_pages").exists())
			.andExpect(jsonPath("$.current_page").value(1))
			.andExpect(jsonPath("$.per_page").value(10));
	}

	@Test
	void createMeterReadingReturnsReading() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reading_date", "2026-01-15",
					"meter_value", 100
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.reading_date").value("2026-01-15"))
			.andExpect(jsonPath("$.meter_value").value(100));
	}

	@Test
	void createMeterReadingWithNotesReturnsReading() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reading_date", "2026-01-16",
					"meter_value", 200,
					"notes", "End of shift"
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reading_date").value("2026-01-16"))
			.andExpect(jsonPath("$.meter_value").value(200))
			.andExpect(jsonPath("$.notes").value("End of shift"));
	}

	@Test
	void createMeterReadingWithoutDateReturns422() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"meter_value", 100
				))))
			.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void createMeterReadingWithoutMeterValueReturns422() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reading_date", "2026-01-17"
				))))
			.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void createDuplicateDateReturns409() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reading_date", "2026-02-01",
					"meter_value", 50
				))))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reading_date", "2026-02-01",
					"meter_value", 75
				))))
			.andExpect(status().isConflict());
	}

	@Test
	void getMeterReadingByIdReturnsReading() throws Exception {
		final String token = loginAsTestUser();

		final String createResponse = mockMvc.perform(post("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reading_date", "2026-03-01",
					"meter_value", 300
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResponse).get("id").asLong();

		mockMvc.perform(get("/api/v1/meter-readings/" + id)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.reading_date").value("2026-03-01"))
			.andExpect(jsonPath("$.meter_value").value(300));
	}

	@Test
	void getNonExistentMeterReadingReturns404() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(get("/api/v1/meter-readings/99999")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound());
	}

	@Test
	void updateMeterReadingReturnsUpdatedReading() throws Exception {
		final String token = loginAsTestUser();

		final String createResponse = mockMvc.perform(post("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reading_date", "2026-04-01",
					"meter_value", 400
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResponse).get("id").asLong();

		mockMvc.perform(put("/api/v1/meter-readings/" + id)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reading_date", "2026-04-01",
					"meter_value", 450,
					"notes", "Updated"
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.meter_value").value(450))
			.andExpect(jsonPath("$.notes").value("Updated"));
	}

	@Test
	void deleteMeterReadingReturnsSuccess() throws Exception {
		final String token = loginAsTestUser();

		final String createResponse = mockMvc.perform(post("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reading_date", "2026-05-01",
					"meter_value", 500
				))))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final Long id = objectMapper.readTree(createResponse).get("id").asLong();

		mockMvc.perform(delete("/api/v1/meter-readings/" + id)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Meter reading deleted successfully."));
	}

	@Test
	void deleteNonExistentMeterReadingReturns404() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(delete("/api/v1/meter-readings/99999")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound());
	}

	@Test
	void listMeterReadingsSortedByDateDescending() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(post("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reading_date", "2026-06-01",
					"meter_value", 10
				))))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reading_date", "2026-06-02",
					"meter_value", 20
				))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/meter-readings")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].reading_date").value("2026-06-02"))
			.andExpect(jsonPath("$.data[1].reading_date").value("2026-06-01"));
	}

	@Test
	void listMeterReadingsWithPaginationAndSearch() throws Exception {
		final String token = loginAsTestUser();

		for (int i = 1; i <= 15; i++) {
			mockMvc.perform(post("/api/v1/meter-readings")
					.header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"reading_date", "2026-07-" + String.format("%02d", i),
						"meter_value", 10 * i,
						"notes", "Day " + i
					))))
				.andExpect(status().isOk());
		}

		mockMvc.perform(get("/api/v1/meter-readings?page=2&size=5")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(5))
			.andExpect(jsonPath("$.total_items").value(15))
			.andExpect(jsonPath("$.total_pages").value(3))
			.andExpect(jsonPath("$.current_page").value(2))
			.andExpect(jsonPath("$.per_page").value(5));

		mockMvc.perform(get("/api/v1/meter-readings?page=1&size=10&search=2026-07-15")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.total_items").value(1))
			.andExpect(jsonPath("$.data[0].reading_date").value("2026-07-15"));
	}
}
