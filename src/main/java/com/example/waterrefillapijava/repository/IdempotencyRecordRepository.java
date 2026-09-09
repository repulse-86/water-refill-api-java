package com.example.waterrefillapijava.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.waterrefillapijava.model.IdempotencyRecord;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

	@Modifying
	@Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now")
	int deleteExpiredBefore(@Param("now") Instant now);
}
