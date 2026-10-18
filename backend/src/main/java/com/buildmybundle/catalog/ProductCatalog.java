package com.buildmybundle.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.buildmybundle.catalog.internal.CatalogService;

import org.springframework.stereotype.Service;

/** Public API of the catalog module: product, variant and category lookups with live price and stock. */
@Service
public class ProductCatalog {

	private final CatalogService catalog;

	public ProductCatalog(CatalogService catalog) {
		this.catalog = catalog;
	}

	/** Products by id, including inactive ones (callers decide how to treat them). Missing ids are absent. */
	public Map<Long, ProductSummary> findByIds(Collection<Long> productIds) {
		return catalog.findByIds(productIds);
	}

	public Optional<ProductSummary> findById(Long productId) {
		return Optional.ofNullable(findByIds(List.of(productId)).get(productId));
	}

	/** Listing cards (colors, sizes, badges, live stock) for specific products, in the order requested. */
	public List<ProductCard> cards(List<Long> productIds) {
		return catalog.cardsByIds(productIds);
	}

	/** Variants by id (active and inactive) with live stock. Missing ids are absent. */
	public Map<Long, VariantSummary> findVariants(Collection<Long> variantIds) {
		return catalog.variants(variantIds);
	}

	public Optional<VariantSummary> findVariant(Long variantId) {
		return Optional.ofNullable(findVariants(List.of(variantId)).get(variantId));
	}

	/** Every variant of a product (active and inactive), with live stock. */
	public List<VariantSummary> variantsOf(Long productId) {
		return catalog.variantsOf(productId);
	}

	/** Active categories (departments and leaves) in display order. */
	public List<CategoryInfo> categories() {
		return catalog.categoryInfos();
	}

	/** Per-category availability for the given category ids; categories without products get empty stats. */
	public Map<Long, CategoryStats> categoryStats(Collection<Long> categoryIds) {
		return catalog.categoryStats(categoryIds);
	}

	/** Specifications and tags of specific products (any status). */
	public List<ProductFacts> facts(Collection<Long> productIds) {
		return catalog.facts(productIds);
	}

	/** Specifications and tags of every active product in the given categories, with live stock. */
	public List<ProductFacts> activeFactsInCategories(Collection<Long> categoryIds) {
		return catalog.activeFactsInCategories(categoryIds);
	}

	/** Category slug → the attribute keys defined for that category. */
	public Map<String, Set<String>> attributeKeysByCategory() {
		return catalog.attributeKeysByCategory();
	}

	/** Brands of active products. */
	public Set<String> brands() {
		return catalog.brandNames();
	}

	/** Colors offered by active product variants (canonical spelling). */
	public Set<String> colorNames() {
		return catalog.colorNames();
	}

	/** Usage tags present on products. */
	public Set<String> tags() {
		return catalog.tags();
	}
}
