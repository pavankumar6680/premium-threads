package com.buildmybundle.catalog.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.buildmybundle.catalog.internal.CatalogModels.BrandSummary;
import com.buildmybundle.catalog.internal.CatalogModels.NamedRef;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Brands, styles and occasions: the storefront's browsing vocabularies. */
@Repository
public class BrandQueries {

	private static final String BRAND_SELECT = """
			SELECT b.id, b.slug, b.name, b.description, b.featured,
			       count(p.id) AS product_count,
			       coalesce(string_agg(DISTINCT d.slug, ',' ), '') AS departments
			FROM brands b
			LEFT JOIN products p ON p.brand_id = b.id AND p.status = 'ACTIVE'
			LEFT JOIN categories c ON c.id = p.category_id
			LEFT JOIN categories d ON d.id = c.parent_id
			WHERE b.active
			""";

	private static final RowMapper<BrandSummary> BRAND = (rs, n) -> new BrandSummary(rs.getLong("id"),
			rs.getString("slug"), rs.getString("name"), rs.getString("description"), rs.getBoolean("featured"),
			rs.getLong("product_count"),
			Arrays.stream(rs.getString("departments").split(",")).filter(s -> !s.isBlank()).sorted().toList());

	private final JdbcClient jdbc;

	public BrandQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Active brands that have at least one product on sale, featured first. */
	public List<BrandSummary> brands() {
		return jdbc.sql(BRAND_SELECT + """
				GROUP BY b.id HAVING count(p.id) > 0
				ORDER BY b.featured DESC, b.sort_order, b.name
				""").query(BRAND).list();
	}

	public Optional<BrandSummary> brand(String slug) {
		return jdbc.sql(BRAND_SELECT + " AND b.slug = :slug GROUP BY b.id").param("slug", slug).query(BRAND).optional();
	}

	public List<NamedRef> styles() {
		return jdbc.sql("SELECT slug, name FROM styles WHERE active ORDER BY sort_order, name")
			.query((rs, n) -> new NamedRef(rs.getString("slug"), rs.getString("name")))
			.list();
	}

	public List<NamedRef> occasions() {
		return jdbc.sql("SELECT slug, name FROM occasions WHERE active ORDER BY sort_order, name")
			.query((rs, n) -> new NamedRef(rs.getString("slug"), rs.getString("name")))
			.list();
	}
}
