package com.example.waterrefillapijava.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.exception.ConflictException;
import com.example.waterrefillapijava.exception.FieldValidationException;
import com.example.waterrefillapijava.exception.NotFoundException;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductType;
import com.example.waterrefillapijava.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;

	@Transactional(readOnly = true)
	public Page<Product> listAll(Pageable pageable) {
		return productRepository.findAll(pageable);
	}

	@Transactional(readOnly = true)
	public Page<Product> search(String search, ProductType type, Pageable pageable) {
		if (search != null && type != null) {
			return productRepository.findByNameContainingIgnoreCaseAndType(search, type, pageable);
		}
		if (search != null) {
			return productRepository.findByNameContainingIgnoreCase(search, pageable);
		}
		if (type != null) {
			return productRepository.findByType(type, pageable);
		}
		return productRepository.findAll(pageable);
	}

	@Transactional(readOnly = true)
	public Product findById(Long id) {
		return productRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Product not found."));
	}

	@Transactional
	public Product create(String name, ProductType type, BigDecimal volumeGallons,
			BigDecimal price, Integer stockQuantity, Integer reorderPoint, String image) {
		if (productRepository.existsByNameIgnoreCase(name)) {
			throw new ConflictException("The name has already been taken.");
		}

		if (type == ProductType.water_refill) {
			if (volumeGallons == null || volumeGallons.compareTo(BigDecimal.ZERO) <= 0) {
				throw FieldValidationException.builder()
					.add("volume_gallons", "The volume must be a positive number.")
					.build();
			}
		}

		final Product product = Product.builder()
			.name(name.trim())
			.type(type)
			.volumeGallons(type == ProductType.water_refill ? volumeGallons : null)
			.price(price)
			.stockQuantity(stockQuantity)
			.reorderPoint(reorderPoint)
			.image(image)
			.build();

		return productRepository.save(product);
	}

	@Transactional
	public Product update(
		Long id,
		String name,
		ProductType type,
		BigDecimal volumeGallons,
		BigDecimal price,
		Integer stockQuantity,
		Integer reorderPoint,
		String image
	) {
		final Product product = findById(id);

		if (!product.getName().equalsIgnoreCase(name) && productRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw new ConflictException("The name has already been taken.");
		}

		if (type == ProductType.water_refill) {
			if (volumeGallons == null || volumeGallons.compareTo(BigDecimal.ZERO) <= 0) {
				throw FieldValidationException.builder()
					.add("volume_gallons", "The volume must be a positive number.")
					.build();
			}
		}

		product.setName(name.trim());
		product.setType(type);
		product.setVolumeGallons(type == ProductType.water_refill ? volumeGallons : null);
		product.setPrice(price);
		product.setStockQuantity(stockQuantity);
		product.setReorderPoint(reorderPoint);
		product.setImage(image);

		return productRepository.save(product);
	}

	@Transactional
	public void delete(Long id) {
		if (!productRepository.existsById(id)) {
			throw new NotFoundException("Product not found.");
		}
		productRepository.deleteById(id);
	}
}
