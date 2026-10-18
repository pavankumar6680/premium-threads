package com.buildmybundle.catalog.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.buildmybundle.catalog.CategoryInfo;
import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.catalog.internal.CatalogModels.AttributeFacet;
import com.buildmybundle.catalog.internal.CatalogModels.AttributeValue;
import com.buildmybundle.catalog.internal.CatalogModels.BrandRef;
import com.buildmybundle.catalog.internal.CatalogModels.CardRow;
import com.buildmybundle.catalog.internal.CatalogModels.CategoryNode;
import com.buildmybundle.catalog.internal.CatalogModels.CategoryRef;
import com.buildmybundle.catalog.internal.CatalogModels.ColorFacet;
import com.buildmybundle.catalog.internal.CatalogModels.FacetValue;
import com.buildmybundle.catalog.internal.CatalogModels.Facets;
import com.buildmybundle.catalog.internal.CatalogModels.NamedRef;
import com.buildmybundle.catalog.internal.CatalogModels.ProductImage;
import com.buildmybundle.catalog.internal.CatalogModels.ProductSort;
import com.buildmybundle.catalog.internal.CatalogModels.SearchCriteria;
import com.buildmybundle.catalog.internal.CatalogModels.VariantRow;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL read model for the catalog. Search is plain SQL rather than JPA because it combines PostgreSQL
 * full-text search, a recursive category scope, attribute predicates on the EAV table and variant
 * predicates (size in stock, color). Every dynamic fragment is a fixed string; all user input is bound as a
 * parameter. The size filter reads the inventory module's {@code variant_stock} table read-only, so that
 * "in stock in my size" can be filtered and paged in one query.
 */
@Repository
public class ProductQueries {

	private static final String SUMMARY_COLUMNS = """
			p.id, p.sku, p.slug, p.name, p.brand, p.price, p.rating_avg, p.rating_count, p.quality_tier, p.status,
			c.slug AS category_slug, c.name AS category_name""";

	private static final String CARD_COLUMNS = SUMMARY_COLUMNS + """
			, b.slug AS brand_slug, p.compare_at_price, p.gender, p.new_arrival, p.trending, p.featured""";

	private static final String FROM_PRODUCTS = " FROM products p JOIN categories c ON c.id = p.category_id ";

	private static final String FROM_CARDS = FROM_PRODUCTS + "JOIN brands b ON b.id = p.brand_id ";

	/** Canonical text form of an attribute value, shared by facets and filters so they always match. */
	private static final String VALUE_EXPR = "COALESCE(pa.value_text, pa.value_bool::text, trim_scale(pa.value_number)::text)";

	/** Orders sizes naturally: XS < S < M < L < XL < XXL, then numeric waist sizes, then "One size". */
	static final String SIZE_RANK = """
			CASE %1$s WHEN 'XXS' THEN 1 WHEN 'XS' THEN 2 WHEN 'S' THEN 3 WHEN 'M' THEN 4 WHEN 'L' THEN 5
			WHEN 'XL' THEN 6 WHEN 'XXL' THEN 7 WHEN 'XXXL' THEN 8
			ELSE CASE WHEN %1$s ~ '^[0-9]+$' THEN 100 + %1$s::int ELSE 1000 END END""";

	private static final int MAX_QUERY_TERMS = 8;

	private static final RowMapper<ProductSummary> SUMMARY = (rs, n) -> new ProductSummary(rs.getLong("id"),
			rs.getString("sku"), rs.getString("slug"), rs.getString("name"), rs.getString("brand"),
			rs.getBigDecimal("price"), rs.getBigDecimal("rating_avg"), rs.getInt("rating_count"),
			rs.getInt("quality_tier"), rs.getString("category_slug"), rs.getString("category_name"),
			"ACTIVE".equals(rs.getString("status")), 0);

	private static final RowMapper<CardRow> CARD = (rs, n) -> new CardRow(SUMMARY.mapRow(rs, n),
			rs.getString("brand_slug"), rs.getBigDecimal("compare_at_price"), rs.getString("gender"),
			rs.getBoolean("new_arrival"), rs.getBoolean("trending"), rs.getBoolean("featured"));

	private final JdbcClient jdbc;

	public ProductQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// ------------------------------------------------------------------ lookups

	public List<ProductSummary> findByIds(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("SELECT " + SUMMARY_COLUMNS + FROM_PRODUCTS + "WHERE p.id IN (:ids)")
			.param("ids", ids)
			.query(SUMMARY)
			.list();
	}

	public List<CardRow> cardsByIds(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("SELECT " + CARD_COLUMNS + FROM_CARDS + "WHERE p.id IN (:ids)").param("ids", ids).query(CARD).list();
	}

	/** Variants of the given products (active and inactive), in display order. */
	public List<VariantRow> variantsOfProducts(Collection<Long> productIds) {
		if (productIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT id, product_id, sku, size, color, color_hex, color_family, active FROM product_variants
				WHERE product_id IN (:ids) ORDER BY product_id, sort_order, id
				""").param("ids", productIds).query(VARIANT).list();
	}

	public List<VariantRow> variantsByIds(Collection<Long> variantIds) {
		if (variantIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT id, product_id, sku, size, color, color_hex, color_family, active FROM product_variants
				WHERE id IN (:ids)
				""").param("ids", variantIds).query(VARIANT).list();
	}

	private static final RowMapper<VariantRow> VARIANT = (rs, n) -> new VariantRow(rs.getLong("id"),
			rs.getLong("product_id"), rs.getString("sku"), rs.getString("size"), rs.getString("color"),
			rs.getString("color_hex"), rs.getString("color_family"), rs.getBoolean("active"));

	/** First image per product (any color), for listing cards. */
	public Map<Long, String> primaryImages(Collection<Long> productIds) {
		Map<Long, String> result = new HashMap<>();
		if (productIds.isEmpty()) {
			return result;
		}
		jdbc.sql("""
				SELECT DISTINCT ON (product_id) product_id, url FROM product_images
				WHERE product_id IN (:ids) ORDER BY product_id, sort_order, id
				""").param("ids", productIds).query(rs -> {
			result.put(rs.getLong("product_id"), rs.getString("url"));
		});
		return result;
	}

	// ------------------------------------------------------------------ search

	public long count(SearchCriteria criteria) {
		Filter f = filter(criteria, true);
		return jdbc.sql(f.cte() + "SELECT count(*)" + FROM_CARDS + "WHERE " + f.where())
			.params(f.params())
			.query(Long.class)
			.single();
	}

	public List<CardRow> search(SearchCriteria criteria) {
		Filter f = filter(criteria, true);
		Map<String, Object> params = new HashMap<>(f.params());
		params.put("limit", criteria.size());
		params.put("offset", (long) criteria.page() * criteria.size());
		return jdbc
			.sql(f.cte() + "SELECT " + CARD_COLUMNS + FROM_CARDS + "WHERE " + f.where() + " ORDER BY "
					+ orderBy(criteria.sort(), f.params().containsKey("tsq")) + " LIMIT :limit OFFSET :offset")
			.params(params)
			.query(CARD)
			.list();
	}

	public Facets facets(SearchCriteria criteria) {
		Filter f = filter(criteria, false);
		List<FacetValue> brands = jdbc
			.sql(f.cte() + "SELECT p.brand AS value, count(*) AS count" + FROM_CARDS + "WHERE " + f.where()
					+ " GROUP BY p.brand ORDER BY p.brand")
			.params(f.params())
			.query((rs, n) -> new FacetValue(rs.getString("value"), rs.getLong("count")))
			.list();
		BigDecimal[] range = jdbc
			.sql(f.cte() + "SELECT min(p.price) AS min_price, max(p.price) AS max_price" + FROM_CARDS + "WHERE "
					+ f.where())
			.params(f.params())
			.query((rs, n) -> new BigDecimal[] { rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price") })
			.single();
		List<FacetValue> sizes = jdbc.sql(f.cte() + "SELECT v.size AS value, count(DISTINCT p.id) AS count" + FROM_CARDS
				+ "JOIN product_variants v ON v.product_id = p.id AND v.active "
				+ "JOIN variant_stock s ON s.variant_id = v.id AND s.quantity_on_hand > 0 WHERE " + f.where()
				+ " GROUP BY v.size ORDER BY " + SIZE_RANK.formatted("v.size") + ", v.size")
			.params(f.params())
			.query((rs, n) -> new FacetValue(rs.getString("value"), rs.getLong("count")))
			.list();
		List<ColorFacet> colors = jdbc.sql(f.cte() + "SELECT v.color AS value, min(v.color_hex) AS hex, "
				+ "count(DISTINCT p.id) AS count" + FROM_CARDS
				+ "JOIN product_variants v ON v.product_id = p.id AND v.active WHERE " + f.where()
				+ " AND v.color <> 'Standard' GROUP BY v.color ORDER BY count(DISTINCT p.id) DESC, v.color")
			.params(f.params())
			.query((rs, n) -> new ColorFacet(rs.getString("value"), rs.getString("hex"), rs.getLong("count")))
			.list();
		List<FacetValue> styles = jdbc.sql(f.cte() + "SELECT st.slug AS value, count(*) AS count" + FROM_CARDS
				+ "JOIN product_styles ps ON ps.product_id = p.id JOIN styles st ON st.id = ps.style_id AND st.active "
				+ "WHERE " + f.where() + " GROUP BY st.slug, st.sort_order ORDER BY st.sort_order")
			.params(f.params())
			.query((rs, n) -> new FacetValue(rs.getString("value"), rs.getLong("count")))
			.list();
		List<AttributeFacet> attributes = isLeafCategory(criteria.category()) ? attributeFacets(f) : List.of();
		return new Facets(brands, range[0], range[1], sizes, colors, styles, attributes);
	}

	private List<AttributeFacet> attributeFacets(Filter f) {
		record Row(String key, String label, String type, String unit, String value, long count) {
		}
		List<Row> rows = jdbc.sql(f.cte() + "SELECT ad.key, ad.label, ad.data_type, ad.unit, ad.sort_order, "
				+ VALUE_EXPR + " AS value, min(pa.value_number) AS num, count(DISTINCT p.id) AS count" + FROM_CARDS
				+ "JOIN product_attributes pa ON pa.product_id = p.id "
				+ "JOIN attribute_definitions ad ON ad.id = pa.attribute_id " + "WHERE " + f.where()
				+ " AND ad.filterable "
				+ "GROUP BY ad.key, ad.label, ad.data_type, ad.unit, ad.sort_order, value "
				+ "ORDER BY ad.sort_order, num NULLS LAST, value")
			.params(f.params())
			.query((rs, n) -> new Row(rs.getString("key"), rs.getString("label"), rs.getString("data_type"),
					rs.getString("unit"), rs.getString("value"), rs.getLong("count")))
			.list();
		Map<String, List<Row>> byKey = rows.stream()
			.collect(Collectors.groupingBy(Row::key, LinkedHashMap::new, Collectors.toList()));
		return byKey.values()
			.stream()
			.filter(values -> values.size() > 1) // a single option is not a useful filter
			.map(values -> {
				Row first = values.getFirst();
				return new AttributeFacet(first.key(), first.label(), first.type(), first.unit(),
						values.stream().map(r -> new FacetValue(r.value(), r.count())).toList());
			})
			.toList();
	}

	// ------------------------------------------------------------------ categories

	/** Active departments with their active categories. */
	public List<CategoryNode> categoryTree() {
		record Row(Long id, Long parentId, String slug, String name, String description, long count) {
		}
		List<Row> rows = jdbc.sql("""
				SELECT c.id, c.parent_id, c.slug, c.name, c.description,
				       (SELECT count(*) FROM products p WHERE p.category_id = c.id AND p.status = 'ACTIVE') AS product_count
				FROM categories c
				WHERE c.active
				ORDER BY c.sort_order, c.name
				""")
			.query((rs, n) -> new Row(rs.getLong("id"), (Long) rs.getObject("parent_id"), rs.getString("slug"),
					rs.getString("name"), rs.getString("description"), rs.getLong("product_count")))
			.list();
		Map<Long, List<Row>> children = rows.stream()
			.filter(r -> r.parentId() != null)
			.collect(Collectors.groupingBy(Row::parentId, LinkedHashMap::new, Collectors.toList()));
		return rows.stream().filter(r -> r.parentId() == null).map(dept -> {
			List<CategoryNode> leaves = children.getOrDefault(dept.id(), List.of())
				.stream()
				.map(l -> new CategoryNode(l.id(), l.slug(), l.name(), l.description(), l.count(), List.of()))
				.toList();
			long total = dept.count() + leaves.stream().mapToLong(CategoryNode::productCount).sum();
			return new CategoryNode(dept.id(), dept.slug(), dept.name(), dept.description(), total, leaves);
		}).toList();
	}

	/** Active categories (departments and leaves) in display order. */
	public List<CategoryInfo> categoryInfos() {
		return jdbc.sql("""
				SELECT c.id, c.slug, c.name, d.slug AS department_slug, d.name AS department_name,
				       NOT EXISTS (SELECT 1 FROM categories ch WHERE ch.parent_id = c.id) AS leaf,
				       c.outfit_slot, c.size_scale
				FROM categories c
				LEFT JOIN categories d ON d.id = c.parent_id
				WHERE c.active AND (d.id IS NULL OR d.active)
				ORDER BY coalesce(d.sort_order, c.sort_order), c.parent_id IS NOT NULL, c.sort_order, c.name
				""")
			.query((rs, n) -> new CategoryInfo(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
					rs.getString("department_slug"), rs.getString("department_name"), rs.getBoolean("leaf"),
					rs.getString("outfit_slot"), rs.getString("size_scale")))
			.list();
	}

	// ------------------------------------------------------------------ product detail

	public record DetailRow(CardRow card, String description, Long categoryId, CategoryRef department, BrandRef brand,
			String sizeScale, String material, String care) {
	}

	public Optional<DetailRow> findActiveBySlug(String slug) {
		return jdbc.sql("SELECT " + CARD_COLUMNS + """
				, p.description, p.category_id, p.material, p.care, c.size_scale,
				  d.slug AS department_slug, d.name AS department_name, b.name AS brand_name,
				  b.description AS brand_description
				FROM products p
				JOIN categories c ON c.id = p.category_id
				JOIN brands b ON b.id = p.brand_id
				LEFT JOIN categories d ON d.id = c.parent_id
				WHERE p.slug = :slug AND p.status = 'ACTIVE'
				""").param("slug", slug).query((rs, n) -> new DetailRow(CARD.mapRow(rs, n),
				rs.getString("description"), rs.getLong("category_id"),
				rs.getString("department_slug") == null ? null
						: new CategoryRef(rs.getString("department_slug"), rs.getString("department_name")),
				new BrandRef(rs.getString("brand_slug"), rs.getString("brand_name"), rs.getString("brand_description")),
				rs.getString("size_scale"), rs.getString("material"), rs.getString("care")))
			.optional();
	}

	public List<AttributeValue> attributes(Long productId) {
		record Row(String key, String label, String type, String unit, String value) {
		}
		List<Row> rows = jdbc.sql("SELECT ad.key, ad.label, ad.data_type, ad.unit, " + VALUE_EXPR + """
				 AS value
				FROM product_attributes pa
				JOIN attribute_definitions ad ON ad.id = pa.attribute_id
				WHERE pa.product_id = :id
				ORDER BY ad.sort_order, pa.id
				""")
			.param("id", productId)
			.query((rs, n) -> new Row(rs.getString("key"), rs.getString("label"), rs.getString("data_type"),
					rs.getString("unit"), rs.getString("value")))
			.list();
		Map<String, List<Row>> byKey = rows.stream()
			.collect(Collectors.groupingBy(Row::key, LinkedHashMap::new, Collectors.toList()));
		return byKey.values().stream().map(values -> {
			Row first = values.getFirst();
			return new AttributeValue(first.key(), first.label(), first.type(), first.unit(),
					values.stream().map(Row::value).toList());
		}).toList();
	}

	public List<String> tags(Long productId) {
		return jdbc.sql("SELECT tag FROM product_tags WHERE product_id = :id ORDER BY tag")
			.param("id", productId)
			.query(String.class)
			.list();
	}

	public List<NamedRef> styles(Long productId) {
		return jdbc.sql("""
				SELECT s.slug, s.name FROM product_styles ps JOIN styles s ON s.id = ps.style_id
				WHERE ps.product_id = :id AND s.active ORDER BY s.sort_order
				""").param("id", productId).query((rs, n) -> new NamedRef(rs.getString("slug"), rs.getString("name"))).list();
	}

	public List<NamedRef> occasions(Long productId) {
		return jdbc.sql("""
				SELECT o.slug, o.name FROM product_occasions po JOIN occasions o ON o.id = po.occasion_id
				WHERE po.product_id = :id AND o.active ORDER BY o.sort_order
				""").param("id", productId).query((rs, n) -> new NamedRef(rs.getString("slug"), rs.getString("name"))).list();
	}

	public List<ProductImage> images(Long productId) {
		return jdbc.sql("SELECT url, alt_text, color FROM product_images WHERE product_id = :id ORDER BY sort_order, id")
			.param("id", productId)
			.query((rs, n) -> new ProductImage(rs.getString("url"), rs.getString("alt_text"), rs.getString("color")))
			.list();
	}

	/** Other products in the same category and wardrobe, best rated first. */
	public List<CardRow> related(Long categoryId, Long excludeProductId, String gender, int limit) {
		return jdbc.sql("SELECT " + CARD_COLUMNS + FROM_CARDS + """
				WHERE p.category_id = :categoryId AND p.id <> :id AND p.status = 'ACTIVE'
				  AND (p.gender = :gender OR p.gender = 'UNISEX' OR :gender = 'UNISEX')
				ORDER BY p.rating_avg DESC, p.rating_count DESC, p.id
				LIMIT :limit
				""").param("categoryId", categoryId).param("id", excludeProductId).param("gender", gender)
			.param("limit", limit)
			.query(CARD)
			.list();
	}

	/**
	 * Pieces that complete an outfit with the given product: other outfit slots (or accessories), same
	 * wardrobe or unisex, sharing at least one style. One per category, best rated first.
	 */
	public List<CardRow> completeTheLook(Long productId, int limit) {
		return jdbc.sql("""
				SELECT * FROM (
				    SELECT DISTINCT ON (p.category_id) %s
				    FROM products p
				    JOIN categories c ON c.id = p.category_id
				    JOIN brands b ON b.id = p.brand_id
				    JOIN products me ON me.id = :id
				    JOIN categories mc ON mc.id = me.category_id
				    WHERE p.status = 'ACTIVE' AND p.id <> me.id AND c.active
				      AND coalesce(c.outfit_slot, '') <> coalesce(mc.outfit_slot, '-')
				      AND (p.gender = me.gender OR p.gender = 'UNISEX' OR me.gender = 'UNISEX')
				      AND EXISTS (SELECT 1 FROM product_styles a JOIN product_styles bs ON bs.style_id = a.style_id
				                  WHERE a.product_id = me.id AND bs.product_id = p.id)
				    ORDER BY p.category_id, p.rating_avg DESC, p.rating_count DESC, p.id
				) looks
				ORDER BY rating_avg DESC, rating_count DESC, id
				LIMIT :limit
				""".formatted(CARD_COLUMNS)).param("id", productId).param("limit", limit).query(CARD).list();
	}

	// ------------------------------------------------------------------ inter-module read model

	/** Lightweight rows (id, category, price, brand) of active products, for aggregate statistics. */
	public List<PriceRow> activePriceRows(Collection<Long> categoryIds) {
		if (categoryIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT id, category_id, price, brand FROM products
				WHERE status = 'ACTIVE' AND category_id IN (:ids)
				""")
			.param("ids", categoryIds)
			.query((rs, n) -> new PriceRow(rs.getLong("id"), rs.getLong("category_id"), rs.getBigDecimal("price"),
					rs.getString("brand")))
			.list();
	}

	public record PriceRow(Long productId, Long categoryId, BigDecimal price, String brand) {
	}

	public record SummaryWithCategory(ProductSummary summary, Long categoryId) {
	}

	public List<SummaryWithCategory> summariesWithCategory(Collection<Long> productIds) {
		if (productIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("SELECT " + SUMMARY_COLUMNS + ", p.category_id" + FROM_PRODUCTS + "WHERE p.id IN (:ids)")
			.param("ids", productIds)
			.query((rs, n) -> new SummaryWithCategory(SUMMARY.mapRow(rs, n), rs.getLong("category_id")))
			.list();
	}

	public List<SummaryWithCategory> activeSummariesInCategories(Collection<Long> categoryIds) {
		if (categoryIds.isEmpty()) {
			return List.of();
		}
		return jdbc
			.sql("SELECT " + SUMMARY_COLUMNS + ", p.category_id" + FROM_PRODUCTS
					+ "WHERE p.status = 'ACTIVE' AND p.category_id IN (:ids) ORDER BY p.id")
			.param("ids", categoryIds)
			.query((rs, n) -> new SummaryWithCategory(SUMMARY.mapRow(rs, n), rs.getLong("category_id")))
			.list();
	}

	/** product id → attribute key → canonical values (in definition order). */
	public Map<Long, Map<String, List<String>>> attributeValues(Collection<Long> productIds) {
		Map<Long, Map<String, List<String>>> result = new HashMap<>();
		if (productIds.isEmpty()) {
			return result;
		}
		jdbc.sql("SELECT pa.product_id, ad.key, " + VALUE_EXPR + """
				 AS value
				FROM product_attributes pa
				JOIN attribute_definitions ad ON ad.id = pa.attribute_id
				WHERE pa.product_id IN (:ids)
				ORDER BY pa.product_id, ad.sort_order, pa.id
				""").param("ids", productIds).query(rs -> {
			result.computeIfAbsent(rs.getLong("product_id"), k -> new LinkedHashMap<>())
				.computeIfAbsent(rs.getString("key"), k -> new ArrayList<>())
				.add(rs.getString("value"));
		});
		return result;
	}

	public Map<Long, Set<String>> tagsByProduct(Collection<Long> productIds) {
		Map<Long, Set<String>> result = new HashMap<>();
		if (productIds.isEmpty()) {
			return result;
		}
		jdbc.sql("SELECT product_id, tag FROM product_tags WHERE product_id IN (:ids) ORDER BY tag")
			.param("ids", productIds)
			.query(rs -> {
				result.computeIfAbsent(rs.getLong("product_id"), k -> new java.util.LinkedHashSet<>())
					.add(rs.getString("tag"));
			});
		return result;
	}

	public List<String> activeBrands() {
		return jdbc.sql("SELECT DISTINCT brand FROM products WHERE status = 'ACTIVE' ORDER BY brand")
			.query(String.class)
			.list();
	}

	public List<String> tags() {
		return jdbc.sql("""
				SELECT DISTINCT t.tag FROM product_tags t JOIN products p ON p.id = t.product_id
				WHERE p.status = 'ACTIVE' ORDER BY t.tag
				""").query(String.class).list();
	}

	/** Category slug → attribute keys defined for it. */
	public Map<String, Set<String>> attributeKeysByCategory() {
		Map<String, Set<String>> result = new HashMap<>();
		jdbc.sql("""
				SELECT c.slug, ad.key FROM attribute_definitions ad JOIN categories c ON c.id = ad.category_id
				ORDER BY c.slug, ad.sort_order
				""").query(rs -> {
			result.computeIfAbsent(rs.getString("slug"), k -> new java.util.LinkedHashSet<>()).add(rs.getString("key"));
		});
		return result;
	}

	private boolean isLeafCategory(String slug) {
		if (slug == null) {
			return false;
		}
		return jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM categories c WHERE c.slug = :slug
				    AND NOT EXISTS (SELECT 1 FROM categories ch WHERE ch.parent_id = c.id))
				""").param("slug", slug).query(Boolean.class).single();
	}

	// ------------------------------------------------------------------ filter

	private record Filter(String cte, String where, Map<String, Object> params) {
	}

	/**
	 * Builds the WHERE clause. The "scope" (category, text query, gender, tags) always applies; refinements
	 * (brand, price, rating, size, color, style, occasion, flags, attributes) are skipped for facets.
	 */
	private Filter filter(SearchCriteria c, boolean includeRefinements) {
		List<String> where = new ArrayList<>(List.of("p.status = 'ACTIVE'", "c.active"));
		Map<String, Object> params = new HashMap<>();
		String cte = "";

		if (c.category() != null && !c.category().isBlank()) {
			cte = """
					WITH RECURSIVE cat_scope AS (
					    SELECT id FROM categories WHERE slug = :category
					    UNION ALL
					    SELECT ch.id FROM categories ch JOIN cat_scope s ON ch.parent_id = s.id
					)
					""";
			where.add("p.category_id IN (SELECT id FROM cat_scope)");
			params.put("category", c.category());
		}
		String tsQuery = toPrefixTsQuery(c.q());
		if (tsQuery != null) {
			where.add("p.search_vector @@ to_tsquery('english', :tsq)");
			params.put("tsq", tsQuery);
		}
		if (c.tags() != null && !c.tags().isEmpty()) {
			where.add("EXISTS (SELECT 1 FROM product_tags t WHERE t.product_id = p.id AND t.tag IN (:tags))");
			params.put("tags", c.tags());
		}
		if (c.gender() != null) {
			where.add("p.gender IN (:gender, 'UNISEX')");
			params.put("gender", c.gender());
		}

		if (includeRefinements) {
			if (c.brands() != null && !c.brands().isEmpty()) {
				where.add("(b.name IN (:brands) OR b.slug IN (:brands))");
				params.put("brands", c.brands());
			}
			if (c.minPrice() != null) {
				where.add("p.price >= :minPrice");
				params.put("minPrice", c.minPrice());
			}
			if (c.maxPrice() != null) {
				where.add("p.price <= :maxPrice");
				params.put("maxPrice", c.maxPrice());
			}
			if (c.minRating() != null) {
				where.add("p.rating_avg >= :minRating");
				params.put("minRating", c.minRating());
			}
			if (c.sizes() != null && !c.sizes().isEmpty()) {
				where.add("""
						EXISTS (SELECT 1 FROM product_variants v JOIN variant_stock s ON s.variant_id = v.id
						        WHERE v.product_id = p.id AND v.active AND v.size IN (:sizes) AND s.quantity_on_hand > 0)""");
				params.put("sizes", c.sizes());
			}
			if (c.colors() != null && !c.colors().isEmpty()) {
				where.add("EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.active AND v.color IN (:colors))");
				params.put("colors", c.colors());
			}
			if (c.styles() != null && !c.styles().isEmpty()) {
				where.add("EXISTS (SELECT 1 FROM product_styles ps JOIN styles st ON st.id = ps.style_id "
						+ "WHERE ps.product_id = p.id AND st.slug IN (:styles))");
				params.put("styles", c.styles());
			}
			if (c.occasions() != null && !c.occasions().isEmpty()) {
				where.add("EXISTS (SELECT 1 FROM product_occasions po JOIN occasions o ON o.id = po.occasion_id "
						+ "WHERE po.product_id = p.id AND o.slug IN (:occasions))");
				params.put("occasions", c.occasions());
			}
			for (String flag : c.flags() == null ? List.<String>of() : c.flags()) {
				switch (flag) {
					case "sale" -> where.add("p.compare_at_price IS NOT NULL");
					case "new" -> where.add("p.new_arrival");
					case "trending" -> where.add("p.trending");
					case "featured" -> where.add("p.featured");
					default -> {
						// validated by the controller
					}
				}
			}
			int i = 0;
			for (Map.Entry<String, List<String>> attr : c.attributes().entrySet()) {
				where.add("EXISTS (SELECT 1 FROM product_attributes pa JOIN attribute_definitions ad ON ad.id = pa.attribute_id "
						+ "WHERE pa.product_id = p.id AND ad.key = :attrKey" + i + " AND " + VALUE_EXPR
						+ " IN (:attrValues" + i + "))");
				params.put("attrKey" + i, attr.getKey());
				params.put("attrValues" + i, attr.getValue());
				i++;
			}
		}
		return new Filter(cte, String.join(" AND ", where), params);
	}

	private static String orderBy(ProductSort sort, boolean hasTextQuery) {
		ProductSort effective = sort != null ? sort : (hasTextQuery ? ProductSort.RELEVANCE : ProductSort.POPULAR);
		return switch (effective) {
			case RELEVANCE -> hasTextQuery
					? "ts_rank(p.search_vector, to_tsquery('english', :tsq)) DESC, p.rating_count DESC, p.id"
					: "p.rating_count DESC, p.id";
			case POPULAR -> "p.trending DESC, p.rating_count DESC, p.id";
			case PRICE_ASC -> "p.price ASC, p.id";
			case PRICE_DESC -> "p.price DESC, p.id";
			case RATING -> "p.rating_avg DESC, p.rating_count DESC, p.id";
			case NEWEST -> "p.new_arrival DESC, p.created_at DESC, p.id DESC";
			case DISCOUNT -> "coalesce((p.compare_at_price - p.price) / p.compare_at_price, 0) DESC, p.id";
		};
	}

	/**
	 * "lin shir" → {@code lin:* & shir:*} so results appear while the user is still typing. Terms are reduced
	 * to letters and digits, so the resulting string is always valid tsquery syntax.
	 */
	static String toPrefixTsQuery(String q) {
		if (q == null) {
			return null;
		}
		List<String> terms = Arrays.stream(q.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
			.filter(t -> !t.isBlank())
			.limit(MAX_QUERY_TERMS)
			.map(t -> t + ":*")
			.toList();
		return terms.isEmpty() ? null : String.join(" & ", terms);
	}
}
