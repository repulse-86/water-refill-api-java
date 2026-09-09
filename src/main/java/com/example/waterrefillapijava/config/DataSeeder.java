package com.example.waterrefillapijava.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.waterrefillapijava.model.User;
import com.example.waterrefillapijava.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void run(String... args) {
		if (userRepository.count() == 0) {
			final User admin = User.builder()
				.username("admin")
				.password(passwordEncoder.encode("password"))
				.build();
			userRepository.save(admin);
			log.info("Seeded default admin user (username: admin, password: password)");
		}
	}
}
