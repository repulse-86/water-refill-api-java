package com.example.waterrefillapijava.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.dto.ProductComponentResponse;
import com.example.waterrefillapijava.exception.ConflictException;
import com.example.waterrefillapijava.exception.FieldValidationException;
import com.example.waterrefillapijava.exception.NotFoundException;
import com.example.waterrefillapijava.model.Product;
import com.example.waterrefillapijava.model.ProductComponent;
import com.example.waterrefillapijava.repository.ProductComponentRepository;
import com.example.waterrefillapijava.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductComponentService {

	private final ProductComponentRepository productComponentRepository;
	private final ProductRepository productRepository;

	@Transactional(readOnly = true)
	public Page<ProductComponentResponse> list(Long productId, String search, Pageable pageable) {
		validateProductExists(productId);

		final List<ProductComponent> results;
		if (search != null && !search.isBlank()) {
			results = productComponentRepository.findByProductIdAndComponentNameJoinFetchComponent(productId, search);
		} else {
			results = productComponentRepository.findByProductIdJoinFetchComponent(productId);
		}

		final int start = (int) pageable.getOffset();
		final int end = Math.min(start + pageable.getPageSize(), results.size());
		final List<ProductComponent> pageContent = start < results.size()
			? results.subList(start, end)
			: List.of();

		final Page<ProductComponent> page = new org.springframework.data.domain.PageImpl<>(
			pageContent, pageable, results.size());

		return page.map(this::toResponse);
	}

	@Transactional
	public ProductComponentResponse add(Long productId, Long componentId, Integer quantity) {
		final Product product = productRepository.findById(productId)
			.orElseThrow(() -> new NotFoundException("Product not found."));

		final Product component = productRepository.findById(componentId)
			.orElseThrow(() -> new NotFoundException("Component product not found."));

		if (productId.equals(componentId)) {
			throw FieldValidationException.builder()
				.add("component_id", "A product cannot be a component of itself.")
				.build();
		}

		if (productComponentRepository.existsByProductIdAndComponentId(productId, componentId)) {
			throw new ConflictException("The component has already been added to this product.");
		}

		final ProductComponent pc = ProductComponent.builder()
			.product(product)
			.component(component)
			.quantity(quantity)
			.build();

		return toResponse(productComponentRepository.save(pc));
	}

	@Transactional
	public ProductComponentResponse updateQuantity(Long productId, Long componentId, Integer quantity) {
		final ProductComponent pc = productComponentRepository.findByProductIdAndComponentId(productId, componentId)
			.orElseThrow(() -> new NotFoundException("Product component not found."));

		pc.setQuantity(quantity);
		return toResponse(productComponentRepository.save(pc));
	}

	@Transactional
	public void delete(Long productId, Long componentId) {
		final ProductComponent pc = productComponentRepository.findByProductIdAndComponentId(productId, componentId)
			.orElseThrow(() -> new NotFoundException("Product component not found."));

		productComponentRepository.delete(pc);
	}

	@Transactional(readOnly = true)
	public Map<Long, Integer> getComponentQuantities(Long productId) {
		return productComponentRepository.findByProductId(productId, Pageable.unpaged()).stream()
			.collect(Collectors.toMap(
				pc -> pc.getComponent().getId(),
				ProductComponent::getQuantity
			));
	}

	private void validateProductExists(Long productId) {
		if (!productRepository.existsById(productId)) {
			throw new NotFoundException("Product not found.");
		}
	}

	private ProductComponentResponse toResponse(ProductComponent pc) {
		return new ProductComponentResponse(
			pc.getId(),
			pc.getProduct().getId(),
			pc.getComponent().getId(),
			pc.getComponent().getName(),
			pc.getQuantity()
		);
	}
}
