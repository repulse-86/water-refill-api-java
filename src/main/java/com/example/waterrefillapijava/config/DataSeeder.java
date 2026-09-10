package com.example.waterrefillapijava.config;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.waterrefillapijava.model.Customer;
import com.example.waterrefillapijava.model.Setting;
import com.example.waterrefillapijava.model.User;
import com.example.waterrefillapijava.repository.CustomerRepository;
import com.example.waterrefillapijava.repository.SettingRepository;
import com.example.waterrefillapijava.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

	private final UserRepository userRepository;
	private final SettingRepository settingRepository;
	private final CustomerRepository customerRepository;
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

		if (settingRepository.findById(1L).isEmpty()) {
			final Setting defaults = Setting.builder()
				.id(1L)
				.storeName("My Water Refilling Station")
				.storeAddress("")
				.storePhone("")
				.currency("PHP")
				.lowStockThreshold(10)
				.build();
			settingRepository.save(defaults);
			log.info("Seeded default settings");
		}

		if (customerRepository.count() == 0) {
			customerRepository.save(Customer.builder()
				.name("Juan Dela Cruz").phone("09171234567").email("juan@example.com")
				.subscriberStatus("active").bottleDebt(2).outstandingBalance(new BigDecimal("150")).build());
			customerRepository.save(Customer.builder()
				.name("Maria Santos").phone("09281234567").email("maria@example.com")
				.subscriberStatus("active").bottleDebt(0).outstandingBalance(BigDecimal.ZERO).build());
			customerRepository.save(Customer.builder()
				.name("Pedro Reyes").phone("09391234567").email("pedro@example.com")
				.subscriberStatus("inactive").bottleDebt(5).outstandingBalance(new BigDecimal("400")).build());
			log.info("Seeded 3 default customers");
		}
	}
}
