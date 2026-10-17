package com.buildmybundle.catalog.web;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.catalog.internal.CatalogModels.CategoryNode;
import com.buildmybundle.catalog.internal.CatalogModels.Facets;
import com.buildmybundle.catalog.internal.CatalogModels.ProductDetail;
import com.buildmybundle.catalog.internal.CatalogModels.ProductSort;
import com.buildmybundle.catalog.internal.CatalogModels.SearchCriteria;
import com.buildmybundle.catalog.internal.CatalogService;
import com.buildmybundle.shared.error.ApiException;
import com.buildmybundle.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Catalog")
@RestController
@RequestMapping("/api/v1")
class CatalogController {

	private static final int DEFAULT_PAGE_SIZE = 24;
	private static final int MAX_ATTRIBUTE_FILTERS = 10;
	private static final String ATTR_PREFIX = "attr.";
	private static final Pattern ATTR_KEY = Pattern.compile("[a-z0-9_]{1,60}");

	private final CatalogService catalog;

	CatalogController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@Operation(summary = "Category tree (departments with their categories and product counts)")
	@GetMapping("/categories")
	List<CategoryNode> categories() {
		return catalog.categories();
	}

	@Operation(summary = "Search and filter products",
			description = "Attribute filters use attr.{key}=value and may repeat, e.g. attr.inputs=HDMI&attr.inputs=USB-C. "
					+ "sort: relevance | popular | price_asc | price_desc | rating | newest.")
	@GetMapping("/products")
	PageResponse<ProductSummary> search(@RequestParam(required = false) String q,
			@RequestParam(required = false) String category,
			@RequestParam(name = "brand", required = false) List<String> brands,
			@RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(required = false) BigDecimal minRating,
			@RequestParam(name = "tag", required = false) List<String> tags,
			@RequestParam(required = false) String sort, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> allParams) {
		return catalog.search(criteria(q, category, brands, minPrice, maxPrice, minRating, tags, sort, page, size,
				allParams));
	}

	@Operation(summary = "Filter options (brands, price range, attribute values) for a category or search")
	@GetMapping("/products/facets")
	Facets facets(@RequestParam(required = false) String q, @RequestParam(required = false) String category,
			@RequestParam(name = "tag", required = false) List<String> tags) {
		return catalog.facets(criteria(q, category, null, null, null, null, tags, null, 0, DEFAULT_PAGE_SIZE,
				new org.springframework.util.LinkedMultiValueMap<>()));
	}

	@Operation(summary = "Product details by slug")
	@GetMapping("/products/{slug}")
	ProductDetail product(@PathVariable String slug) {
		return catalog.detail(slug)
			.orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "This product does not exist."));
	}

	private static SearchCriteria criteria(String q, String category, List<String> brands, BigDecimal minPrice,
			BigDecimal maxPrice, BigDecimal minRating, List<String> tags, String sort, Integer page, Integer size,
			MultiValueMap<String, String> allParams) {
		ProductSort productSort = ProductSort.parse(sort);
		if (sort != null && !sort.isBlank() && productSort == null) {
			throw ApiException.badRequest("INVALID_SORT", "Unknown sort option: " + sort);
		}
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw ApiException.badRequest("INVALID_PRICE_RANGE", "minPrice must not exceed maxPrice.");
		}
		return new SearchCriteria(q, category, nonBlank(brands), minPrice, maxPrice, minRating, nonBlank(tags),
				attributeFilters(allParams), productSort, PageResponse.clampPage(page),
				PageResponse.clampSize(size, DEFAULT_PAGE_SIZE));
	}

	private static Map<String, List<String>> attributeFilters(MultiValueMap<String, String> params) {
		Map<String, List<String>> filters = new LinkedHashMap<>();
		params.forEach((name, values) -> {
			if (!name.startsWith(ATTR_PREFIX)) {
				return;
			}
			String key = name.substring(ATTR_PREFIX.length());
			if (!ATTR_KEY.matcher(key).matches()) {
				throw ApiException.badRequest("INVALID_ATTRIBUTE_FILTER", "Invalid attribute filter: " + name);
			}
			List<String> nonBlank = nonBlank(values);
			if (!nonBlank.isEmpty()) {
				filters.put(key, nonBlank);
			}
		});
		if (filters.size() > MAX_ATTRIBUTE_FILTERS) {
			throw ApiException.badRequest("TOO_MANY_FILTERS", "At most " + MAX_ATTRIBUTE_FILTERS
					+ " attribute filters are allowed.");
		}
		return filters;
	}

	private static List<String> nonBlank(List<String> values) {
		return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).toList();
	}
}
