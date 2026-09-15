package com.example.waterrefillapijava.setting.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "settings")
public class Setting {

	@Id
	private Long id;

	@Column(name = "store_name", nullable = false)
	private String storeName;

	@Column(name = "store_address")
	private String storeAddress;

	@Column(name = "store_phone")
	private String storePhone;

	@Column(nullable = false)
	private String currency;

	@Column(name = "low_stock_threshold", nullable = false)
	private Integer lowStockThreshold;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "modified_at")
	private LocalDateTime modifiedAt;

	@PrePersist
	public void onCreate() {
		this.createdAt = LocalDateTime.now();
		this.modifiedAt = LocalDateTime.now();
	}

	@PreUpdate
	public void onUpdate() {
		this.modifiedAt = LocalDateTime.now();
	}
}
