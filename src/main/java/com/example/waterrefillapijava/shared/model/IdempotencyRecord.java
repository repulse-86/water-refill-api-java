package com.example.waterrefillapijava.shared.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

	public enum Status {
		IN_PROGRESS, COMPLETED, FAILED
	}

	@Id
	@Column(name = "idempotency_key")
	private String idempotencyKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Lob
	@Column(name = "result_payload")
	private String resultPayload;

	@Column(name = "result_type")
	private String resultType;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
}
