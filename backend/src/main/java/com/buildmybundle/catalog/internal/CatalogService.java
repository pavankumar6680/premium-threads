package com.buildmybundle.catalog.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.buildmybundle.catalog.CategoryInfo;
import com.buildmybundle.catalog.CategoryStats;
import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.catalog.VariantSummary;
import com.buildmybundle.catalog.internal.CatalogModels.BrandSummary;
import com.buildmybundle.catalog.internal.CatalogModels.CardRow;
import com.buildmybundle.catalog.internal.CatalogModels.CategoryNode;
import com.buildmybundle.catalog.internal.CatalogModels.ColorOption;
import com.buildmybundle.catalog.internal.CatalogModels.Facets;
import com.buildmybundle.catalog.internal.CatalogModels.ProductCard;
import com.buildmybundle.catalog.internal.CatalogModels.ProductDetail;
import com.buildmybundle.catalog.internal.CatalogModels.SearchCriteria;
import com.buildmybundle.catalog.internal.CatalogModels.Variant;
import com.buildmybundle.catalog.internal.CatalogModels.VariantRow;
import com.buildmybundle.catalog.internal.CatalogModels.Vocabulary;
import com.buildmybundle.catalog.internal.ProductQueries.PriceRow;
import com.buildmybundle.catalog.internal.ProductQueries.SummaryWithCategory;
import com.buildmybundle.inventory.InventoryService;
import com.buildmybundle.shared.web.PageResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog reads, enriched with variants and live stock from the inventory module. A product's available
 * quantity is the sum over its active variants.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

	private static final int RELATED_LIMIT = 4;
	private static final int LOOK_LIMIT = 4;
	private static final List<String> SIZE_ORDER = List.of("XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL");

	private final ProductQueries queries;
	private final BrandQueries brands;
	private final InventoryService inventory;

	public CatalogService(ProductQueries queries, BrandQueries brands, InventoryService inventory) {
		this.queries = queries;
		this.brands = brands;
		this.inventory = inventory;
	}

	// ------------------------------------------------------------------ storefront

	public PageResponse<ProductCard> search(SearchCriteria criteria) {
		long total = queries.count(criteria);
		List<ProductCard> content = total == 0 ? List.of() : cards(queries.search(criteria));
		return PageResponse.of(content, criteria.page(), criteria.size(), total);
	}

	public Facets facets(SearchCriteria criteria) {
		return queries.facets(criteria);
	}

	public List<CategoryNode> categories() {
		return queries.categoryTree();
	}

	public List<BrandSummary> brands() {
		return brands.brands();
	}

	public Optional<BrandSummary> brand(String slug) {
		return brands.brand(slug);
	}

	public Vocabulary vocabulary() {
		return new Vocabulary(brands.styles(), brands.occasions());
	}

	public Optional<ProductDetail> detail(String slug) {
		return queries.findActiveBySlug(slug).map(row -> {
			Long id = row.card().summary().id();
			List<VariantRow> variantRows = queries.variantsOfProducts(List.of(id));
			Map<Long, Integer> stock = inventory.availableQuantities(variantRows.stream().map(VariantRow::id).toList());
			List<Variant> variants = variantRows.stream()
				.filter(VariantRow::active)
				.sorted(sizeOrder())
				.map(v -> new Variant(v.id(), v.sku(), v.size(), v.color(), v.colorHex(), stock.getOrDefault(v.id(), 0)))
				.toList();
			ProductCard product = card(row.card(), variantRows, stock, queries.primaryImages(List.of(id)).get(id));
			return new ProductDetail(product, row.description(), row.department(), row.brand(), row.sizeScale(),
					row.material(), row.care(), variants, queries.styles(id), queries.occasions(id), queries.attributes(id),
					queries.tags(id), queries.images(id),
					cards(queries.related(row.categoryId(), id, row.card().gender(), RELATED_LIMIT)),
					cards(queries.completeTheLook(id, LOOK_LIMIT)));
		});
	}

	// ------------------------------------------------------------------ inter-module API

	public Map<Long, ProductSummary> findByIds(Collection<Long> ids) {
		return withStock(queries.findByIds(ids)).stream()
			.collect(Collectors.toMap(ProductSummary::id, Function.identity()));
	}

	public Map<Long, VariantSummary> variants(Collection<Long> variantIds) {
		return toSummaries(queries.variantsByIds(variantIds)).stream()
			.collect(Collectors.toMap(VariantSummary::id, Function.identity()));
	}

	/** Every variant of a product (active and inactive), sizes in natural order. */
	public List<VariantSummary> variantsOf(Long productId) {
		return toSummaries(queries.variantsOfProducts(List.of(productId))).stream().sorted(Comparator
			.comparing(VariantSummary::color)
			.thenComparing(v -> sizeRank(v.size()))).toList();
	}

	public List<CategoryInfo> categoryInfos() {
		return queries.categoryInfos();
	}

	public Map<Long, CategoryStats> categoryStats(Collection<Long> categoryIds) {
		List<PriceRow> rows = queries.activePriceRows(categoryIds);
		Map<Long, Integer> stock = stockByProduct(rows.stream().map(PriceRow::productId).toList());
		Map<Long, List<PriceRow>> byCategory = rows.stream().collect(Collectors.groupingBy(PriceRow::categoryId));
		Map<Long, CategoryStats> stats = new LinkedHashMap<>();
		for (Long categoryId : categoryIds) {
			List<PriceRow> all = byCategory.getOrDefault(categoryId, List.of());
			List<PriceRow> inStock = all.stream().filter(r -> stock.getOrDefault(r.productId(), 0) > 0).toList();
			stats.put(categoryId, new CategoryStats(categoryId, all.size(), inStock.size(),
					inStock.stream().map(PriceRow::price).min(BigDecimal::compareTo).orElse(null),
					inStock.stream().map(PriceRow::price).max(BigDecimal::compareTo).orElse(null),
					inStock.stream().map(PriceRow::brand).distinct().sorted().toList()));
		}
		return stats;
	}

	/** Facts for specific products (any status), in the order requested. */
	public List<ProductFacts> facts(Collection<Long> productIds) {
		return toFacts(queries.summariesWithCategory(productIds));
	}

	/** Facts for every active product in the given categories. */
	public List<ProductFacts> activeFactsInCategories(Collection<Long> categoryIds) {
		return toFacts(queries.activeSummariesInCategories(categoryIds));
	}

	public Set<String> brandNames() {
		return new LinkedHashSet<>(queries.activeBrands());
	}

	public Set<String> tags() {
		return new LinkedHashSet<>(queries.tags());
	}

	public Map<String, Set<String>> attributeKeysByCategory() {
		return queries.attributeKeysByCategory();
	}

	// ------------------------------------------------------------------ helpers

	private List<ProductFacts> toFacts(List<SummaryWithCategory> rows) {
		List<Long> ids = rows.stream().map(r -> r.summary().id()).toList();
		Map<Long, Integer> stock = stockByProduct(ids);
		Map<Long, Map<String, List<String>>> attributes = queries.attributeValues(ids);
		Map<Long, Set<String>> tags = queries.tagsByProduct(ids);
		return rows.stream()
			.map(r -> new ProductFacts(r.summary().withAvailableQuantity(stock.getOrDefault(r.summary().id(), 0)),
					r.categoryId(), attributes.getOrDefault(r.summary().id(), Map.of()),
					tags.getOrDefault(r.summary().id(), Set.of())))
			.toList();
	}

	private List<ProductSummary> withStock(List<ProductSummary> products) {
		Map<Long, Integer> stock = stockByProduct(products.stream().map(ProductSummary::id).toList());
		return products.stream().map(p -> p.withAvailableQuantity(stock.getOrDefault(p.id(), 0))).toList();
	}

	/** Units on hand per product: the sum over its active variants. */
	private Map<Long, Integer> stockByProduct(Collection<Long> productIds) {
		List<VariantRow> variants = queries.variantsOfProducts(productIds).stream().filter(VariantRow::active).toList();
		Map<Long, Integer> stock = inventory.availableQuantities(variants.stream().map(VariantRow::id).toList());
		return variants.stream()
			.collect(Collectors.groupingBy(VariantRow::productId, Collectors.summingInt(v -> stock.getOrDefault(v.id(), 0))));
	}

	private List<VariantSummary> toSummaries(List<VariantRow> rows) {
		Map<Long, Integer> stock = inventory.availableQuantities(rows.stream().map(VariantRow::id).toList());
		return rows.stream()
			.map(v -> new VariantSummary(v.id(), v.productId(), v.sku(), v.size(), v.color(), v.colorHex(),
					v.colorFamily(), v.active(), stock.getOrDefault(v.id(), 0)))
			.toList();
	}

	private List<ProductCard> cards(List<CardRow> rows) {
		List<Long> ids = rows.stream().map(r -> r.summary().id()).toList();
		List<VariantRow> variants = queries.variantsOfProducts(ids);
		Map<Long, Integer> stock = inventory.availableQuantities(variants.stream().map(VariantRow::id).toList());
		Map<Long, List<VariantRow>> byProduct = variants.stream()
			.collect(Collectors.groupingBy(VariantRow::productId, LinkedHashMap::new, Collectors.toList()));
		Map<Long, String> images = queries.primaryImages(ids);
		return rows.stream()
			.map(r -> card(r, byProduct.getOrDefault(r.summary().id(), List.of()), stock, images.get(r.summary().id())))
			.toList();
	}

	private static ProductCard card(CardRow row, List<VariantRow> variants, Map<Long, Integer> stock, String imageUrl) {
		List<VariantRow> active = variants.stream().filter(VariantRow::active).toList();
		int available = active.stream().mapToInt(v -> stock.getOrDefault(v.id(), 0)).sum();
		List<ColorOption> colors = new ArrayList<>();
		Set<String> seenColors = new LinkedHashSet<>();
		for (VariantRow v : active) {
			if (!"Standard".equals(v.color()) && seenColors.add(v.color())) {
				colors.add(new ColorOption(v.color(), v.colorHex()));
			}
		}
		List<String> sizes = active.stream().map(VariantRow::size).distinct().sorted(Comparator.comparingInt(CatalogService::sizeRank)).toList();
		List<String> inStock = active.stream()
			.filter(v -> stock.getOrDefault(v.id(), 0) > 0)
			.map(VariantRow::size)
			.distinct()
			.sorted(Comparator.comparingInt(CatalogService::sizeRank))
			.toList();
		ProductSummary p = row.summary();
		return new ProductCard(p.id(), p.sku(), p.slug(), p.name(), p.brand(), row.brandSlug(), p.price(),
				row.compareAtPrice(), p.ratingAvg(), p.ratingCount(), p.qualityTier(), p.categorySlug(), p.categoryName(),
				row.gender(), p.active(), available, row.newArrival(), row.trending(), row.featured(), colors, sizes,
				inStock, imageUrl);
	}

	private static Comparator<VariantRow> sizeOrder() {
		return Comparator.comparing((VariantRow v) -> sizeRank(v.size())).thenComparing(VariantRow::color);
	}

	/** Natural size order: XS < S < M < L < XL < XXL, numeric waist sizes, then anything else. */
	static int sizeRank(String size) {
		int alpha = SIZE_ORDER.indexOf(size);
		if (alpha >= 0) {
			return alpha;
		}
		if (size.matches("\\d+")) {
			return 100 + Integer.parseInt(size);
		}
		return 1000;
	}
}
