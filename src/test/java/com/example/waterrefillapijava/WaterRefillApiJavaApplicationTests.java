package com.example.waterrefillapijava;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.ProductRepository;
import com.example.waterrefillapijava.repository.RefreshTokenRepository;
import com.example.waterrefillapijava.repository.SettingRepository;
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
	private SettingRepository settingRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private ProductRepository productRepository;

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
		customerRepository.deleteAll();
		productRepository.deleteAll();
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

	@Test
	void listCustomersReturnsAll() throws Exception {
		final String token = loginAsTestUser();

		mockMvc.perform(get("/api/v1/customers")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());
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
					"subscriber_status", "inactive"
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
					"subscriber_status", "active"
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
					"subscriber_status", "active"
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

	// ==================== Product Tests ====================

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

	// ==================== Helper Methods ====================

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
