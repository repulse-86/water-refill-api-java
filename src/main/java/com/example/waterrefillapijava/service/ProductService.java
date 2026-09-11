package com.example.waterrefillapijava.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.dto.ComponentItem;
import com.example.waterrefillapijava.exception.ConflictException;
import com.example.waterrefillapijava.exception.FieldValidationException;
import com.example.waterrefillapijava.exception.NotFoundException;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductComponent;
import com.example.waterrefillapijava.model.ProductType;
import com.example.waterrefillapijava.repository.ProductComponentRepository;
import com.example.waterrefillapijava.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;
	private final ProductComponentRepository productComponentRepository;

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
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:product-performance")
	})
	public Product create(String name, ProductType type, BigDecimal volumeGallons,
			BigDecimal price, Integer stockQuantity, Integer reorderPoint, String image,
			List<ComponentItem> components) {
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

		productRepository.save(product);

		if (components != null && !components.isEmpty()) {
			saveComponents(product, components);
		}

		return product;
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:product-performance")
	})
	public Product update(
		Long id,
		String name,
		ProductType type,
		BigDecimal volumeGallons,
		BigDecimal price,
		Integer stockQuantity,
		Integer reorderPoint,
		String image,
		List<ComponentItem> components
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

		productRepository.save(product);

		if (components != null) {
			productComponentRepository.findByProductId(id, Pageable.unpaged()).forEach(productComponentRepository::delete);
			if (!components.isEmpty()) {
				saveComponents(product, components);
			}
		}

		return product;
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:product-performance")
	})
	public void delete(Long id) {
		if (!productRepository.existsById(id)) {
			throw new NotFoundException("Product not found.");
		}
		productComponentRepository.findByProductId(id, Pageable.unpaged()).forEach(productComponentRepository::delete);
		productRepository.deleteById(id);
	}

	private void saveComponents(Product product, List<ComponentItem> components) {
		for (var item : components) {
			final Product componentProduct = productRepository.findById(item.componentId())
				.orElseThrow(() -> new NotFoundException("Component product not found."));

			if (product.getId().equals(componentProduct.getId())) {
				throw FieldValidationException.builder()
					.add("component_id", "A product cannot be a component of itself.")
					.build();
			}

			if (productComponentRepository.existsByProductIdAndComponentId(product.getId(), componentProduct.getId())) {
				throw new ConflictException("The component has already been added to this product.");
			}

			final ProductComponent pc = ProductComponent.builder()
				.product(product)
				.component(componentProduct)
				.quantity(item.quantity())
				.build();

			productComponentRepository.save(pc);
		}
	}
}
