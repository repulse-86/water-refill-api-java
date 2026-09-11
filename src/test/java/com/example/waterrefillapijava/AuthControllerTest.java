package com.example.waterrefillapijava;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerTest extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void pingReturnsOk() throws Exception {
		mockMvc.perform(get("/api/v1/ping"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void loginWithValidCredentialsReturnsToken() throws Exception {
		final Map<String, Object> body = Map.of(
			"username", "testuser",
			"password", "testpass123",
			"remember", false
		);

		mockMvc.perform(post("/api/v1/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isNotEmpty())
			.andExpect(jsonPath("$.user.id").isNumber())
			.andExpect(jsonPath("$.user.username").value("testuser"));
	}

	@Test
	void loginWithInvalidCredentialsReturns401() throws Exception {
		final Map<String, Object> body = Map.of(
			"username", "testuser",
			"password", "wrongpassword",
			"remember", false
		);

		mockMvc.perform(post("/api/v1/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.message").value("Your credentials do not exist in our records."));
	}

	@Test
	void loginWithNonExistentUserReturns401() throws Exception {
		final Map<String, Object> body = Map.of(
			"username", "nobody",
			"password", "password",
			"remember", false
		);

		mockMvc.perform(post("/api/v1/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void unauthenticatedAccessToProtectedEndpointReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void authenticatedAccessToMeEndpointReturnsUser() throws Exception {
		final Map<String, Object> loginBody = Map.of(
			"username", "testuser",
			"password", "testpass123",
			"remember", false
		);

		final String loginResponse = mockMvc.perform(post("/api/v1/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(loginBody)))
			.andReturn()
			.getResponse()
			.getContentAsString();

		final String token = objectMapper.readTree(loginResponse).get("token").asText();

		mockMvc.perform(get("/api/v1/me")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.username").value("testuser"));
	}

	@Test
	void loginWithMissingFieldsReturns422() throws Exception {
		mockMvc.perform(post("/api/v1/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors").isMap());
	}

	@Test
	void refreshRotationWorksAndOldTokenRevoked() throws Exception {
		final Map<String, Object> body = Map.of(
			"username", "testuser",
			"password", "testpass123",
			"remember", false
		);

		final MvcResult loginResult = mockMvc.perform(post("/api/v1/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
			.andExpect(status().isOk())
			.andReturn();

		final String refreshToken = extractRefreshToken(loginResult.getResponse());
		assertTrue(refreshToken != null && !refreshToken.isBlank(), "refresh_token cookie should be set");

		final MvcResult refreshResult = mockMvc.perform(post("/api/v1/refresh")
				.cookie(new Cookie("refresh_token", refreshToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isNotEmpty())
			.andExpect(jsonPath("$.user.username").value("testuser"))
			.andReturn();

		final String newRefreshToken = extractRefreshToken(refreshResult.getResponse());
		assertTrue(newRefreshToken != null && !newRefreshToken.isBlank(), "new refresh_token cookie should be set");

		mockMvc.perform(post("/api/v1/refresh")
				.cookie(new Cookie("refresh_token", refreshToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshWithoutTokenReturns401() throws Exception {
		mockMvc.perform(post("/api/v1/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesTokens() throws Exception {
		final Map<String, Object> body = Map.of(
			"username", "testuser",
			"password", "testpass123",
			"remember", false
		);

		final MvcResult loginResult = mockMvc.perform(post("/api/v1/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
			.andExpect(status().isOk())
			.andReturn();

		final String refreshToken = extractRefreshToken(loginResult.getResponse());

		mockMvc.perform(post("/api/v1/logout")
				.cookie(new Cookie("refresh_token", refreshToken))
				.header("Authorization", "Bearer " + objectMapper.readTree(
					loginResult.getResponse().getContentAsString()).get("token").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Logged out successfully"));

		mockMvc.perform(post("/api/v1/refresh")
				.cookie(new Cookie("refresh_token", refreshToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized());
	}
}
