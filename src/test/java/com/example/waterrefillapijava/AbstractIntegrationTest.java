package com.example.waterrefillapijava;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.waterrefillapijava.model.User;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.MeterReadingRepository;
import com.example.waterrefillapijava.repository.OrderItemRepository;
import com.example.waterrefillapijava.repository.OrderRepository;
import com.example.waterrefillapijava.repository.ProductComponentRepository;
import com.example.waterrefillapijava.repository.ProductRepository;
import com.example.waterrefillapijava.repository.RefreshTokenRepository;
import com.example.waterrefillapijava.repository.SettingRepository;
import com.example.waterrefillapijava.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected RefreshTokenRepository refreshTokenRepository;

	@Autowired
	protected SettingRepository settingRepository;

	@Autowired
	protected CustomerRepository customerRepository;

	@Autowired
	protected ProductRepository productRepository;

	@Autowired
	protected ProductComponentRepository productComponentRepository;

	@Autowired
	protected OrderRepository orderRepository;

	@Autowired
	protected OrderItemRepository orderItemRepository;

	@Autowired
	protected MeterReadingRepository meterReadingRepository;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	@Autowired
	protected ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		orderItemRepository.deleteAll();
		orderRepository.deleteAll();
		meterReadingRepository.deleteAll();
		userRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		customerRepository.deleteAll();
		productComponentRepository.deleteAll();
		productRepository.deleteAll();
		final User user = User.builder()
			.username("testuser")
			.password(passwordEncoder.encode("testpass123"))
			.build();
		userRepository.save(user);
	}

	protected String loginAsTestUser() throws Exception {
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

	protected String extractRefreshToken(final MockHttpServletResponse response) {
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

	protected String createProduct(final String token, final Map<String, Object> fields) throws Exception {
		return mockMvc.perform(post("/api/v1/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(fields)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	protected Long extractId(final String json) throws Exception {
		return objectMapper.readTree(json).get("id").asLong();
	}

	protected String createMultipartProduct(final String token, final Map<String, Object> fields) throws Exception {
		return mockMvc.perform(multipart("/api/v1/products")
				.file(new org.springframework.mock.web.MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(fields)))
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}
}
