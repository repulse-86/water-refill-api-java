package com.example.waterrefillapijava.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.waterrefillapijava.model.User;
import com.example.waterrefillapijava.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	public Optional<User> loadByUsername(final String username) {
		return userRepository.findByUsername(username);
	}
}
