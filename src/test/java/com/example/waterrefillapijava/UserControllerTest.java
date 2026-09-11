package com.example.waterrefillapijava;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.example.waterrefillapijava.model.User;

import java.util.Map;

class UserControllerTest extends AbstractIntegrationTest {

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
}
