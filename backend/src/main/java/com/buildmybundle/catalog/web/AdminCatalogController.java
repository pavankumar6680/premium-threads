package com.buildmybundle.catalog.web;

import java.util.List;

import jakarta.validation.Valid;

import com.buildmybundle.catalog.admin.AdminCatalogModels.AdminCategory;
import com.buildmybundle.catalog.admin.AdminCatalogModels.AdminProduct;
import com.buildmybundle.catalog.admin.AdminCatalogModels.AdminProductRow;
import com.buildmybundle.catalog.admin.AdminCatalogModels.AttributeRequest;
import com.buildmybundle.catalog.admin.AdminCatalogModels.CategoryRequest;
import com.buildmybundle.catalog.admin.AdminCatalogModels.ProductRequest;
import com.buildmybundle.catalog.admin.AdminCatalogModels.StatusRequest;
import com.buildmybundle.catalog.admin.AdminCatalogModels.StockRequest;
import com.buildmybundle.catalog.admin.CategoryAdminService;
import com.buildmybundle.catalog.admin.ProductAdminService;
import com.buildmybundle.catalog.admin.ProductAdminService.Sort;
import com.buildmybundle.shared.error.ApiException;
import com.buildmybundle.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: catalog")
@RestController
@RequestMapping("/api/v1/admin")
class AdminCatalogController {

	private static final int DEFAULT_PAGE_SIZE = 25;

	private final ProductAdminService products;
	private final CategoryAdminService categories;

	AdminCatalogController(ProductAdminService products, CategoryAdminService categories) {
		this.products = products;
		this.categories = categories;
	}

	@Operation(summary = "Products for administration (any status), with stock",
			description = "sort: name | price | updated | stock. lowStock=true lists products with 5 or fewer units.")
	@GetMapping("/products")
	PageResponse<AdminProductRow> list(@RequestParam(required = false) String q,
			@RequestParam(required = false) String category, @RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "false") boolean lowStock, @RequestParam(required = false) String sort,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return products.list(q, category, active, lowStock, parseSort(sort), PageResponse.clampPage(page),
				PageResponse.clampSize(size, DEFAULT_PAGE_SIZE));
	}

	@Operation(summary = "A product with its attributes, tags and version")
	@GetMapping("/products/{id}")
	AdminProduct get(@PathVariable Long id) {
		return products.get(id);
	}

	@Operation(summary = "Create a product")
	@PostMapping("/products")
	@ResponseStatus(HttpStatus.CREATED)
	AdminProduct create(@Valid @RequestBody ProductRequest request) {
		return products.create(request);
	}

	@Operation(summary = "Update a product (optimistic locking: send the version you edited)")
	@PutMapping("/products/{id}")
	AdminProduct update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
		return products.update(id, request);
	}

	@Operation(summary = "Set the stock level")
	@PutMapping("/products/{id}/stock")
	AdminProduct setStock(@PathVariable Long id, @Valid @RequestBody StockRequest request) {
		return products.setStock(id, request.quantity());
	}

	@Operation(summary = "Activate or deactivate a product")
	@PatchMapping("/products/{id}/status")
	AdminProduct setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
		return products.setActive(id, request.active());
	}

	@Operation(summary = "All categories with their attribute definitions")
	@GetMapping("/categories")
	List<AdminCategory> categories() {
		return categories.list();
	}

	@Operation(summary = "Create a category in a department")
	@PostMapping("/categories")
	@ResponseStatus(HttpStatus.CREATED)
	AdminCategory createCategory(@Valid @RequestBody CategoryRequest request) {
		return categories.create(request);
	}

	@Operation(summary = "Add an attribute definition to a category")
	@PostMapping("/categories/{slug}/attributes")
	@ResponseStatus(HttpStatus.CREATED)
	AdminCategory addAttribute(@PathVariable String slug, @Valid @RequestBody AttributeRequest request) {
		return categories.addAttribute(slug, request);
	}

	private static Sort parseSort(String sort) {
		if (sort == null || sort.isBlank()) {
			return Sort.NAME;
		}
		try {
			return Sort.valueOf(sort.trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.badRequest("INVALID_SORT", "Unknown sort option: " + sort);
		}
	}
}
