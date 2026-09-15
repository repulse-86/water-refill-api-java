package com.example.waterrefillapijava.auth.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.waterrefillapijava.shared.model.User;
import com.example.waterrefillapijava.shared.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	public Optional<User> loadByUsername(final String username) {
		return userRepository.findByUsername(username);
	}

	public User save(final User user) {
		return userRepository.save(user);
	}

	public boolean existsByUsernameAndIdNot(final String username, final Long id) {
		return userRepository.existsByUsernameAndIdNot(username, id);
	}
}
