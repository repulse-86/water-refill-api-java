package com.example.waterrefillapijava.shared.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Entity
@Table(name = "refresh_tokens", indexes = {
	@Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JsonIgnore
	@NonNull
	@Column(nullable = false, unique = true)
	private String tokenHash;

	@JsonIgnore
	@NonNull
	@Column(nullable = false)
	private String tokenFamily;

	@NonNull
	@Column(nullable = false)
	private Long userId;

	@NonNull
	@Column(nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private boolean revoked = false;

	@JsonIgnore
	@Column(nullable = true)
	private String fingerprint;
}
