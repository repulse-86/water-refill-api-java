package com.example.waterrefillapijava.product.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.product.dto.ComponentItem;
import com.example.waterrefillapijava.shared.exception.ConflictException;
import com.example.waterrefillapijava.shared.exception.FieldValidationException;
import com.example.waterrefillapijava.shared.exception.NotFoundException;
import com.example.waterrefillapijava.product.model.Product;
import com.example.waterrefillapijava.product.model.ProductComponent;
import com.example.waterrefillapijava.product.model.ProductType;
import com.example.waterrefillapijava.order.repository.OrderItemRepository;
import com.example.waterrefillapijava.product.repository.ProductComponentRepository;
import com.example.waterrefillapijava.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;
	private final ProductComponentRepository productComponentRepository;
	private final OrderItemRepository orderItemRepository;

	@Transactional(readOnly = true)
	public Page<Product> listAll(Pageable pageable) {
		return productRepository.findByDeletedFalse(pageable);
	}

	@Transactional(readOnly = true)
	public Page<Product> search(String search, ProductType type, Pageable pageable) {
		if (search != null && type != null) {
			return productRepository.findByNameContainingIgnoreCaseAndTypeAndDeletedFalse(search, type, pageable);
		}
		if (search != null) {
			return productRepository.findByNameContainingIgnoreCaseAndDeletedFalse(search, pageable);
		}
		if (type != null) {
			return productRepository.findByTypeAndDeletedFalse(type, pageable);
		}
		return productRepository.findByDeletedFalse(pageable);
	}

	@Transactional(readOnly = true)
	public Product findById(Long id) {
		final Product product = productRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Product not found."));
		if (product.isDeleted()) {
			throw new NotFoundException("Product not found.");
		}
		return product;
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
		final Product product = productRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Product not found."));
		product.setDeleted(true);
		product.setDeletedAt(java.time.LocalDateTime.now());
		productRepository.save(product);
	}

	@Transactional(readOnly = true)
	public Page<Product> archiveList(String search, ProductType type, Pageable pageable) {
		if (search != null && type != null) {
			return productRepository.findByNameContainingIgnoreCaseAndTypeAndDeletedTrue(search, type, pageable);
		}
		if (search != null) {
			return productRepository.findByNameContainingIgnoreCaseAndDeletedTrue(search, pageable);
		}
		if (type != null) {
			return productRepository.findByTypeAndDeletedTrue(type, pageable);
		}
		return productRepository.findByDeletedTrue(pageable);
	}

	@Transactional
	@Caching(evict = {
		@CacheEvict("dashboard"),
		@CacheEvict("report:product-performance")
	})
	public void restore(Long id) {
		final Product product = productRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Product not found."));
		if (!product.isDeleted()) {
			throw new ConflictException("Product is not archived.");
		}
		if (productRepository.existsByNameIgnoreCaseAndIdNot(product.getName(), id)) {
			throw new ConflictException("A product with this name already exists.");
		}
		product.setDeleted(false);
		product.setDeletedAt(null);
		productRepository.save(product);
	}

	@Transactional
	public String permanentDelete(Long id) {
		final Product product = productRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Product not found."));
		if (!product.isDeleted()) {
			throw new ConflictException("Product must be archived before permanent deletion.");
		}
		if (orderItemRepository.existsByProductId(id)) {
			throw new ConflictException("Cannot permanently delete: product is referenced by order history.");
		}
		final String imageUrl = product.getImage();
		productComponentRepository.deleteByProductId(id);
		productComponentRepository.deleteByComponentId(id);
		productRepository.deleteById(id);
		return imageUrl;
	}

	private void saveComponents(Product product, List<ComponentItem> components) {
		final List<Long> componentIds = components.stream()
			.map(ComponentItem::componentId)
			.toList();
		final Map<Long, Product> productsById = productRepository.findAllById(componentIds).stream()
			.collect(Collectors.toMap(Product::getId, p -> p));

		final List<ProductComponent> toSave = new java.util.ArrayList<>();
		for (var item : components) {
			final Product componentProduct = productsById.get(item.componentId());
			if (componentProduct == null) {
				throw new NotFoundException("Component product not found.");
			}

			if (product.getId().equals(componentProduct.getId())) {
				throw FieldValidationException.builder()
					.add("component_id", "A product cannot be a component of itself.")
					.build();
			}

			if (productComponentRepository.existsByProductIdAndComponentId(product.getId(), componentProduct.getId())) {
				throw new ConflictException("The component has already been added to this product.");
			}

			toSave.add(ProductComponent.builder()
				.product(product)
				.component(componentProduct)
				.quantity(item.quantity())
				.build());
		}

		productComponentRepository.saveAll(toSave);
	}
}
