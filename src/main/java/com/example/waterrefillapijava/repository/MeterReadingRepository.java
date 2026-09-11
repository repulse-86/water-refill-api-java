package com.example.waterrefillapijava.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.waterrefillapijava.model.MeterReading;

public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {

	boolean existsByReadingDate(LocalDate readingDate);

	boolean existsByReadingDateAndIdNot(LocalDate readingDate, Long id);

	Optional<MeterReading> findTopByReadingDateBeforeOrderByReadingDateDesc(LocalDate readingDate);

	@Query("SELECT m FROM MeterReading m WHERE CAST(m.readingDate AS string) LIKE %:search% OR m.notes LIKE %:search%")
	Page<MeterReading> findBySearch(@Param("search") String search, Pageable pageable);
}
