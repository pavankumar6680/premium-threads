package com.buildmybundle.catalog.internal;

import java.math.BigDecimal;
import java.util.Collection;
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
import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.catalog.internal.ProductQueries.PriceRow;
import com.buildmybundle.catalog.internal.ProductQueries.SummaryWithCategory;
import com.buildmybundle.catalog.internal.CatalogModels.CategoryNode;
import com.buildmybundle.catalog.internal.CatalogModels.Facets;
import com.buildmybundle.catalog.internal.CatalogModels.ProductDetail;
import com.buildmybundle.catalog.internal.CatalogModels.SearchCriteria;
import com.buildmybundle.inventory.InventoryService;
import com.buildmybundle.shared.web.PageResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog reads, enriched with live stock from the inventory module. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

	private static final int RELATED_LIMIT = 4;

	private final ProductQueries queries;
	private final InventoryService inventory;

	public CatalogService(ProductQueries queries, InventoryService inventory) {
		this.queries = queries;
		this.inventory = inventory;
	}

	public PageResponse<ProductSummary> search(SearchCriteria criteria) {
		long total = queries.count(criteria);
		List<ProductSummary> content = total == 0 ? List.of() : withStock(queries.search(criteria));
		return PageResponse.of(content, criteria.page(), criteria.size(), total);
	}

	public Facets facets(SearchCriteria criteria) {
		return queries.facets(criteria);
	}

	public List<CategoryNode> categories() {
		return queries.categoryTree();
	}

	public Optional<ProductDetail> detail(String slug) {
		return queries.findActiveBySlug(slug).map(row -> {
			ProductSummary product = withStock(List.of(row.summary())).getFirst();
			return new ProductDetail(product, row.description(), row.department(),
					queries.attributes(product.id()), queries.tags(product.id()), queries.images(product.id()),
					withStock(queries.related(row.categoryId(), product.id(), RELATED_LIMIT)));
		});
	}

	public Map<Long, ProductSummary> findByIds(Collection<Long> ids) {
		return withStock(queries.findByIds(ids)).stream()
			.collect(Collectors.toMap(ProductSummary::id, Function.identity()));
	}

	public List<CategoryInfo> categoryInfos() {
		return queries.categoryInfos();
	}

	public Map<Long, CategoryStats> categoryStats(Collection<Long> categoryIds) {
		List<PriceRow> rows = queries.activePriceRows(categoryIds);
		Map<Long, Integer> stock = inventory.availableQuantities(rows.stream().map(PriceRow::productId).toList());
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

	private List<ProductFacts> toFacts(List<SummaryWithCategory> rows) {
		List<Long> ids = rows.stream().map(r -> r.summary().id()).toList();
		Map<Long, Integer> stock = inventory.availableQuantities(ids);
		Map<Long, Map<String, List<String>>> attributes = queries.attributeValues(ids);
		Map<Long, Set<String>> tags = queries.tagsByProduct(ids);
		return rows.stream()
			.map(r -> new ProductFacts(r.summary().withAvailableQuantity(stock.getOrDefault(r.summary().id(), 0)),
					r.categoryId(), attributes.getOrDefault(r.summary().id(), Map.of()),
					tags.getOrDefault(r.summary().id(), Set.of())))
			.toList();
	}

	public Set<String> brands() {
		return new LinkedHashSet<>(queries.activeBrands());
	}

	public Set<String> tags() {
		return new LinkedHashSet<>(queries.tags());
	}

	private List<ProductSummary> withStock(List<ProductSummary> products) {
		Map<Long, Integer> stock = inventory.availableQuantities(products.stream().map(ProductSummary::id).toList());
		return products.stream().map(p -> p.withAvailableQuantity(stock.getOrDefault(p.id(), 0))).toList();
	}
}
