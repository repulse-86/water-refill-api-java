package com.example.waterrefillapijava.meterreading.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.waterrefillapijava.meterreading.dto.projection.MeterReadingValue;
import com.example.waterrefillapijava.meterreading.model.MeterReading;

public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {

	boolean existsByReadingDate(LocalDate readingDate);

	boolean existsByReadingDateAndIdNot(LocalDate readingDate, Long id);

	Optional<MeterReading> findTopByReadingDateBeforeOrderByReadingDateDesc(LocalDate readingDate);

	@Query("SELECT m FROM MeterReading m WHERE m.deleted = false AND (CAST(m.readingDate AS string) LIKE %:search% OR m.notes LIKE %:search%)")
	Page<MeterReading> findBySearch(@Param("search") String search, Pageable pageable);

	Page<MeterReading> findByDeletedFalse(Pageable pageable);

	@Query("SELECT m FROM MeterReading m WHERE m.deleted = false AND (CAST(m.readingDate AS string) LIKE %:search% OR m.notes LIKE %:search%)")
	Page<MeterReading> findBySearchAndDeletedFalse(@Param("search") String search, Pageable pageable);

	Page<MeterReading> findByDeletedTrue(Pageable pageable);

	@Query("SELECT m FROM MeterReading m WHERE m.deleted = true AND (CAST(m.readingDate AS string) LIKE %:search% OR m.notes LIKE %:search%)")
	Page<MeterReading> findBySearchAndDeletedTrue(@Param("search") String search, Pageable pageable);


	@Query("SELECT new com.example.waterrefillapijava.meterreading.dto.projection.MeterReadingValue(m.readingDate, m.meterValue) FROM MeterReading m WHERE m.deleted = false ORDER BY m.readingDate DESC")
	List<MeterReadingValue> findReadingValuesByDeletedFalse();

	@Query("SELECT new com.example.waterrefillapijava.meterreading.dto.projection.MeterReadingValue(m.readingDate, m.meterValue) FROM MeterReading m WHERE m.readingDate < :date AND m.deleted = false ORDER BY m.readingDate DESC")
	List<MeterReadingValue> findPreviousReadingValues(@Param("date") LocalDate date);
}
