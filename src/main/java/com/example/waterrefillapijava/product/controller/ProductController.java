package com.example.waterrefillapijava.product.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.example.waterrefillapijava.shared.dto.MessageResponse;
import com.example.waterrefillapijava.shared.dto.PageResponse;
import com.example.waterrefillapijava.product.dto.ProductComponentRequest;
import com.example.waterrefillapijava.product.dto.ProductComponentResponse;
import com.example.waterrefillapijava.product.dto.ProductRequest;
import com.example.waterrefillapijava.product.dto.ProductResponse;
import com.example.waterrefillapijava.product.dto.ProductUpdateRequest;
import com.example.waterrefillapijava.product.model.Product;
import com.example.waterrefillapijava.product.model.ProductType;
import com.example.waterrefillapijava.product.service.FileStorageService;
import com.example.waterrefillapijava.product.service.ProductComponentService;
import com.example.waterrefillapijava.product.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

	private final ProductService productService;
	private final ProductComponentService productComponentService;
	private final FileStorageService fileStorageService;

	@GetMapping
	public ResponseEntity<PageResponse<ProductResponse>> list(
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search,
		@RequestParam(required = false) ProductType type
	) {
		final Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, size)),
			Sort.by("name").ascending());

		final Page<Product> products = productService.search(search, type, pageable);

		return ResponseEntity.ok(toPageResponse(products));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ProductResponse> get(@PathVariable final Long id) {
		final Product product = productService.findById(id);
		return ResponseEntity.ok(toProductResponse(product));
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ProductResponse> createJson(@RequestBody @Valid final ProductRequest request) {
		return ResponseEntity.ok(doCreate(request, null));
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ProductResponse> createMultipart(
		@RequestPart("product") @Valid final ProductRequest request,
		@RequestPart(value = "image", required = false) MultipartFile imageFile
	) {
		return ResponseEntity.ok(doCreate(request, imageFile));
	}

	private ProductResponse doCreate(final ProductRequest request, final MultipartFile imageFile) {
		String imageUrl = request.image();
		if (imageFile != null && !imageFile.isEmpty()) {
			imageUrl = fileStorageService.storeFile(imageFile);
		}

		final Product product = productService.create(
			request.name(), request.type(), request.volumeGallons(),
			request.price(), request.stockQuantity(), request.reorderPoint(), imageUrl,
			request.components()
		);

		log.info("Product created: id={}, name={}", product.getId(), product.getName());

		return toProductResponse(product);
	}

	@PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ProductResponse> update(
		@PathVariable final Long id,
		@RequestPart("product") @Valid final ProductUpdateRequest request,
		@RequestPart(value = "image", required = false) MultipartFile imageFile
	) {
		String imageUrl = request.image();
		if (imageFile != null && !imageFile.isEmpty()) {
			imageUrl = fileStorageService.storeFile(imageFile);
		}

		final Product product = productService.update(
			id, request.name(), request.type(), request.volumeGallons(),
			request.price(), request.stockQuantity(), request.reorderPoint(), imageUrl,
			request.components()
		);

		log.info("Product updated: id={}, name={}", product.getId(), product.getName());

		return ResponseEntity.ok(toProductResponse(product));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable final Long id) {
		productService.delete(id);

		log.info("Product deleted: id={}", id);

		return ResponseEntity.ok(new MessageResponse("Product deleted successfully."));
	}

	@GetMapping("/{productId}/components")
	public ResponseEntity<PageResponse<ProductComponentResponse>> listComponents(
		@PathVariable final Long productId,
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search
	) {
		final Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, size)),
			Sort.by("id").ascending());

		final Page<ProductComponentResponse> components = productComponentService.list(productId, search, pageable);

		return ResponseEntity.ok(toComponentPageResponse(components));
	}

	@PostMapping("/{productId}/components")
	public ResponseEntity<ProductComponentResponse> addComponent(
		@PathVariable final Long productId,
		@Valid @RequestBody final ProductComponentRequest request
	) {
		final ProductComponentResponse component = productComponentService.add(
			productId, request.componentId(), request.quantity()
		);

		log.info("Component added: productId={}, componentId={}, quantity={}", productId, request.componentId(), request.quantity());

		return ResponseEntity.ok(component);
	}

	@PutMapping("/{productId}/components/{componentId}")
	public ResponseEntity<ProductComponentResponse> updateComponent(
		@PathVariable final Long productId,
		@PathVariable final Long componentId,
		@Valid @RequestBody final ProductComponentRequest request
	) {
		final ProductComponentResponse component = productComponentService.updateQuantity(
			productId, componentId, request.quantity()
		);

		log.info("Component updated: productId={}, componentId={}, quantity={}", productId, componentId, request.quantity());

		return ResponseEntity.ok(component);
	}

	@DeleteMapping("/{productId}/components/{componentId}")
	public ResponseEntity<?> deleteComponent(
		@PathVariable final Long productId,
		@PathVariable final Long componentId
	) {
		productComponentService.delete(productId, componentId);

		log.info("Component deleted: productId={}, componentId={}", productId, componentId);

		return ResponseEntity.ok(new MessageResponse("Product component deleted successfully."));
	}

	private PageResponse<ProductResponse> toPageResponse(final Page<Product> page) {
		return new PageResponse<>(
			page.getContent().stream().map(this::toProductResponse).toList(),
			page.getNumber() + 1,
			page.getSize(),
			page.getTotalElements(),
			page.getTotalPages()
		);
	}

	private PageResponse<ProductComponentResponse> toComponentPageResponse(final Page<ProductComponentResponse> page) {
		return new PageResponse<>(
			page.getContent(),
			page.getNumber() + 1,
			page.getSize(),
			page.getTotalElements(),
			page.getTotalPages()
		);
	}

	@GetMapping("/deleted")
	public ResponseEntity<PageResponse<ProductResponse>> listDeleted(
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false) String search,
		@RequestParam(required = false) ProductType type
	) {
		final Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, size)),
			Sort.by("id").descending());

		final Page<Product> products = productService.archiveList(search, type, pageable);

		return ResponseEntity.ok(toPageResponse(products));
	}

	@PostMapping("/{id}/restore")
	public ResponseEntity<ProductResponse> restore(@PathVariable final Long id) {
		productService.restore(id);

		log.info("Product restored: id={}", id);

		return ResponseEntity.ok(toProductResponse(productService.findById(id)));
	}

	@DeleteMapping("/{id}/permanent")
	public ResponseEntity<?> permanentDelete(@PathVariable final Long id) {
		final String imageUrl = productService.permanentDelete(id);

		fileStorageService.delete(imageUrl);

		log.info("Product permanently deleted: id={}", id);

		return ResponseEntity.ok(new MessageResponse("Product permanently deleted successfully."));
	}

	private ProductResponse toProductResponse(final Product product) {
		return new ProductResponse(
			product.getId(),
			product.getName(),
			product.getType(),
			product.getVolumeGallons(),
			product.getPrice(),
			product.getStockQuantity(),
			product.getReorderPoint(),
			product.getImage(),
			product.getDeletedAt() != null ? product.getDeletedAt().toString() : null
		);
	}
}
