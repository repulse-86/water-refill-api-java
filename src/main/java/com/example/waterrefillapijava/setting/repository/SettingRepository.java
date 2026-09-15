package com.example.waterrefillapijava.setting.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.waterrefillapijava.setting.model.Setting;

@Repository
public interface SettingRepository extends JpaRepository<Setting, Long> {

	Optional<Setting> findById(Long id);
}
