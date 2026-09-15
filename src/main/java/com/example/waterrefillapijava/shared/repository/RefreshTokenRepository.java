package com.example.waterrefillapijava.shared.repository;

import java.time.Instant;
import java.util.Optional;

import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.waterrefillapijava.shared.model.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(@NonNull String tokenHash);

	@Modifying(clearAutomatically = true)
	@Query(
		"UPDATE RefreshToken r " +
		"SET r.revoked = true " +
		"WHERE r.tokenFamily = :tokenFamily AND r.revoked = false"
	)
	void revokeActiveByTokenFamily(@Param("tokenFamily") @NonNull String tokenFamily);

	@Modifying(clearAutomatically = true)
	@Query(
		"UPDATE RefreshToken r " +
		"SET r.revoked = true " +
		"WHERE r.tokenFamily = :tokenFamily"
	)
	void revokeByTokenFamily(@Param("tokenFamily") @NonNull String tokenFamily);

	@Modifying(clearAutomatically = true)
	@Query(
		"UPDATE RefreshToken r " +
		"SET r.revoked = true " +
		"WHERE r.userId = :userId AND r.revoked = false"
	)
	void revokeAllActiveByUserId(@Param("userId") @NonNull Long userId);

	@Modifying(clearAutomatically = true)
	@Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff")
	int deleteExpiredBefore(@Param("cutoff") @NonNull Instant cutoff);
}
