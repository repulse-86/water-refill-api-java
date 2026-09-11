package com.example.waterrefillapijava.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.model.Setting;
import com.example.waterrefillapijava.repository.SettingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SettingService {

	private static final Long SETTINGS_ID = 1L;

	private final SettingRepository settingRepository;

	@Transactional(readOnly = true)
	@Cacheable("settings")
	public Setting getOrCreate() {
		return settingRepository.findById(SETTINGS_ID).orElseGet(() -> {
			final Setting defaults = Setting.builder()
				.id(SETTINGS_ID)
				.storeName("My Water Refilling Station")
				.storeAddress("")
				.storePhone("")
				.currency("PHP")
				.lowStockThreshold(10)
				.build();
			return settingRepository.save(defaults);
		});
	}

	@Transactional
	@CacheEvict("settings")
	public Setting update(String storeName, String storeAddress, String storePhone,
			String currency, Integer lowStockThreshold) {
		final Setting setting = getOrCreate();
		setting.setStoreName(storeName);
		setting.setStoreAddress(storeAddress);
		setting.setStorePhone(storePhone);
		setting.setCurrency(currency);
		setting.setLowStockThreshold(lowStockThreshold);
		return settingRepository.save(setting);
	}
}
