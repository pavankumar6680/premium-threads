package com.buildmybundle.catalog.web;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.buildmybundle.catalog.internal.CatalogModels.BrandSummary;
import com.buildmybundle.catalog.internal.CatalogModels.CategoryNode;
import com.buildmybundle.catalog.internal.CatalogModels.Facets;
import com.buildmybundle.catalog.ProductCard;
import com.buildmybundle.catalog.internal.CatalogModels.ProductDetail;
import com.buildmybundle.catalog.internal.CatalogModels.ProductSort;
import com.buildmybundle.catalog.internal.CatalogModels.SearchCriteria;
import com.buildmybundle.catalog.internal.CatalogModels.Vocabulary;
import com.buildmybundle.catalog.internal.CatalogService;
import com.buildmybundle.shared.error.ApiException;
import com.buildmybundle.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.util.LinkedMultiValueMap;
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
	private static final Set<String> FLAGS = Set.of("sale", "new", "trending", "featured");
	private static final Set<String> GENDERS = Set.of("MEN", "WOMEN");

	private final CatalogService catalog;

	CatalogController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@Operation(summary = "Category tree (departments with their categories and product counts)")
	@GetMapping("/categories")
	List<CategoryNode> categories() {
		return catalog.categories();
	}

	@Operation(summary = "Brands with products on sale, featured first")
	@GetMapping("/brands")
	List<BrandSummary> brands() {
		return catalog.brands();
	}

	@Operation(summary = "A brand by slug (its products: GET /products?brand={slug})")
	@GetMapping("/brands/{slug}")
	BrandSummary brand(@PathVariable String slug) {
		return catalog.brand(slug).orElseThrow(() -> ApiException.notFound("BRAND_NOT_FOUND", "This brand does not exist."));
	}

	@Operation(summary = "Styles and occasions used for filtering and outfit building")
	@GetMapping("/vocabulary")
	Vocabulary vocabulary() {
		return catalog.vocabulary();
	}

	@Operation(summary = "Search and filter products",
			description = "Repeatable filters: brand (name or slug), sizes (in stock), color, style, occasion, tag, "
					+ "flag (sale | new | trending | featured), attr.{key}=value. gender: MEN or WOMEN (unisex always "
					+ "included). sort: relevance | popular | price_asc | price_desc | rating | newest | discount.")
	@GetMapping("/products")
	PageResponse<ProductCard> search(@RequestParam(required = false) String q,
			@RequestParam(required = false) String category,
			@RequestParam(name = "brand", required = false) List<String> brands,
			@RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(required = false) BigDecimal minRating,
			@RequestParam(name = "tag", required = false) List<String> tags,
			@RequestParam(name = "sizes", required = false) List<String> sizes,
			@RequestParam(name = "color", required = false) List<String> colors,
			@RequestParam(name = "style", required = false) List<String> styles,
			@RequestParam(name = "occasion", required = false) List<String> occasions,
			@RequestParam(required = false) String gender,
			@RequestParam(name = "flag", required = false) List<String> flags,
			@RequestParam(required = false) String sort, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> allParams) {
		ProductSort productSort = ProductSort.parse(sort);
		if (sort != null && !sort.isBlank() && productSort == null) {
			throw ApiException.badRequest("INVALID_SORT", "Unknown sort option: " + sort);
		}
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw ApiException.badRequest("INVALID_PRICE_RANGE", "minPrice must not exceed maxPrice.");
		}
		List<String> flagList = nonBlank(flags).stream().map(f -> f.toLowerCase(Locale.ROOT)).toList();
		flagList.stream().filter(f -> !FLAGS.contains(f)).findFirst().ifPresent(f -> {
			throw ApiException.badRequest("INVALID_FLAG", "Unknown flag: " + f);
		});
		return catalog.search(new SearchCriteria(q, category, nonBlank(brands), minPrice, maxPrice, minRating,
				nonBlank(tags), attributeFilters(allParams), nonBlank(sizes), nonBlank(colors), nonBlank(styles),
				nonBlank(occasions), gender(gender), flagList, productSort, PageResponse.clampPage(page),
				PageResponse.clampSize(size, DEFAULT_PAGE_SIZE)));
	}

	@Operation(summary = "Filter options (brands, price range, sizes, colors, styles, attributes) for a scope")
	@GetMapping("/products/facets")
	Facets facets(@RequestParam(required = false) String q, @RequestParam(required = false) String category,
			@RequestParam(name = "tag", required = false) List<String> tags,
			@RequestParam(required = false) String gender) {
		return catalog.facets(new SearchCriteria(q, category, List.of(), null, null, null, nonBlank(tags),
				attributeFilters(new LinkedMultiValueMap<>()), List.of(), List.of(), List.of(), List.of(), gender(gender),
				List.of(), null, 0, DEFAULT_PAGE_SIZE));
	}

	@Operation(summary = "Product details by slug: variants with stock, images, styles and matching pieces")
	@GetMapping("/products/{slug}")
	ProductDetail product(@PathVariable String slug) {
		return catalog.detail(slug)
			.orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "This product does not exist."));
	}

	private static String gender(String gender) {
		if (gender == null || gender.isBlank()) {
			return null;
		}
		String upper = gender.trim().toUpperCase(Locale.ROOT);
		if (!GENDERS.contains(upper)) {
			throw ApiException.badRequest("INVALID_GENDER", "gender must be MEN or WOMEN.");
		}
		return upper;
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
