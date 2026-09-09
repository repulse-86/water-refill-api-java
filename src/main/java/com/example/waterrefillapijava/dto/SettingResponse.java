package com.example.waterrefillapijava.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.waterrefillapijava.model.Setting;

public record SettingResponse(
	Long id,

	@JsonProperty("store_name")
	String storeName,

	@JsonProperty("store_address")
	String storeAddress,

	@JsonProperty("store_phone")
	String storePhone,

	String currency,

	@JsonProperty("low_stock_threshold")
	Integer lowStockThreshold
) {
	public SettingResponse(final Setting setting) {
		this(
			setting.getId(),
			setting.getStoreName(),
			setting.getStoreAddress(),
			setting.getStorePhone(),
			setting.getCurrency(),
			setting.getLowStockThreshold()
		);
	}
}
