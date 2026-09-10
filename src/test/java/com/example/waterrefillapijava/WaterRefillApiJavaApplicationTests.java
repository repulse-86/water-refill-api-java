package com.example.waterrefillapijava;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.waterrefillapijava.model.User;
import com.example.waterrefillapijava.repository.RefreshTokenRepository;
import com.example.waterrefillapijava.repository.UserRepository;
import com.example.waterrefillapijava.security.ApiRateLimiter;
import com.example.waterrefillapijava.security.SlidingWindowRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WaterRefillApiJavaApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SlidingWindowRateLimiter slidingWindowRateLimiter;

	@Autowired
	private ApiRateLimiter apiRateLimiter;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		final User user = User.builder()
			.username("testuser")
			.password(passwordEncoder.encode("testpass123"))
			.build();
		userRepository.save(user);
	}

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
	void rateLimiterThrottlesAndResets() {
		final String key = "test-rate-limit";

		assertFalse(slidingWindowRateLimiter.isRateLimited(key, 3, 60000));
		assertFalse(slidingWindowRateLimiter.isRateLimited(key, 3, 60000));
		assertFalse(slidingWindowRateLimiter.isRateLimited(key, 3, 60000));
		assertTrue(slidingWindowRateLimiter.isRateLimited(key, 3, 60000));

		slidingWindowRateLimiter.reset(key);
		assertFalse(slidingWindowRateLimiter.isRateLimited(key, 3, 60000));
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

	@Test
	void updateProfileReturnsUpdatedUsername() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(put("/api/v1/user/profile-information")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("username", "newname"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.username").value("newname"));
	}

	@Test
	void updateProfileWithDuplicateUsernameReturns422() throws Exception {
		userRepository.save(User.builder().username("taken").password("x").build());
		final String token = loginAsTestUser();

		mockMvc.perform(put("/api/v1/user/profile-information")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("username", "taken"))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors.username").isArray());
	}

	@Test
	void updatePasswordWithWrongCurrentPasswordReturns422() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(put("/api/v1/user/password")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"currentPassword", "wrongpassword",
					"password", "newpass123",
					"passwordConfirmation", "newpass123"
				))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors.current_password").isArray());
	}

	@Test
	void updatePasswordWithMismatchedConfirmationReturns422() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(put("/api/v1/user/password")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"currentPassword", "testpass123",
					"password", "newpass123",
					"passwordConfirmation", "differentpass"
				))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors.password").isArray());
	}

	@Test
	void updatePasswordSuccess() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(put("/api/v1/user/password")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"currentPassword", "testpass123",
					"password", "newpass123",
					"passwordConfirmation", "newpass123"
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Password updated successfully."));
	}

	private String loginAsTestUser() throws Exception {
		final MvcResult result = mockMvc.perform(post("/api/v1/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"username", "testuser",
					"password", "testpass123",
					"remember", false
				))))
			.andExpect(status().isOk())
			.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
	}

	private String extractRefreshToken(final MockHttpServletResponse response) {
		final String setCookie = response.getHeader("Set-Cookie");
		if (setCookie == null) {
			return null;
		}
		return java.util.Arrays.stream(response.getCookies())
			.filter(c -> "refresh_token".equals(c.getName()))
			.map(Cookie::getValue)
			.findFirst()
			.orElse(null);
	}
}
