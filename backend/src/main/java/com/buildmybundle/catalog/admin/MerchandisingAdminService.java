package com.buildmybundle.catalog.admin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.buildmybundle.shared.audit.AuditLog;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchandising vocabulary and placement managed by admins: styles and occasions (used for filters, product
 * tagging and the outfit builder) and the homepage's product rails.
 */
@Service
public class MerchandisingAdminService {

	/** Built-in homepage rails: they keep their place in the layout and cannot be renamed by slug. */
	public static final Set<String> BUILT_IN_SECTIONS = Set.of("trending", "new-arrivals", "offers", "recommended");

	/** Catalog filter keys a homepage rail may use. */
	private static final Set<String> QUERY_KEYS = Set.of("flag", "sort", "gender", "style", "occasion", "brand",
			"category", "department", "q", "colors", "sizes", "minPrice", "maxPrice");

	public enum Vocabulary {
		STYLE("styles", "product_styles", "style_id"), OCCASION("occasions", "product_occasions", "occasion_id");

		final String table;
		final String joinTable;
		final String joinColumn;

		Vocabulary(String table, String joinTable, String joinColumn) {
			this.table = table;
			this.joinTable = joinTable;
			this.joinColumn = joinColumn;
		}
	}

	public record AdminTerm(Long id, String slug, String name, String description, int sortOrder, boolean active,
			long productCount) {
	}

	public record TermRequest(@NotBlank @Size(max = 60) String name,
			@Size(min = 2, max = 40) @Pattern(regexp = AdminCatalogModels.SLUG_PATTERN, message = "lowercase words joined by dashes") String slug,
			@Size(max = 300) String description, @Min(0) @Max(1000) Integer sortOrder, Boolean active) {
	}

	public record HomeSection(Long id, String slug, String eyebrow, String title, String query, int position,
			boolean active, boolean builtIn) {
	}

	public record HomeSectionRequest(
			@Size(min = 2, max = 40) @Pattern(regexp = AdminCatalogModels.SLUG_PATTERN, message = "lowercase words joined by dashes") String slug,
			@Size(max = 60) String eyebrow, @NotBlank @Size(max = 80) String title,
			@NotBlank @Size(max = 300) String query, @Min(0) @Max(1000) Integer position, Boolean active) {
	}

	private final JdbcClient jdbc;
	private final AuditLog audit;

	public MerchandisingAdminService(JdbcClient jdbc, AuditLog audit) {
		this.jdbc = jdbc;
		this.audit = audit;
	}

	// ---------------------------------------------------------------- styles and occasions

	@Transactional(readOnly = true)
	public List<AdminTerm> terms(Vocabulary kind) {
		return jdbc.sql("""
				SELECT t.id, t.slug, t.name, t.description, t.sort_order, t.active, count(p.id) AS product_count
				FROM %s t LEFT JOIN %s j ON j.%s = t.id LEFT JOIN products p ON p.id = j.product_id AND p.status = 'ACTIVE'
				GROUP BY t.id ORDER BY t.sort_order, t.name
				""".formatted(kind.table, kind.joinTable, kind.joinColumn))
			.query((rs, n) -> new AdminTerm(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
					rs.getString("description"), rs.getInt("sort_order"), rs.getBoolean("active"), rs.getLong("product_count")))
			.list();
	}

	@Transactional
	public AdminTerm createTerm(Vocabulary kind, TermRequest request) {
		String name = request.name().trim();
		String slug = request.slug() != null ? request.slug() : ProductAdminService.slugify(name);
		checkUniqueTerm(kind, name, slug, -1L);
		Long id = jdbc.sql("""
				INSERT INTO %s (slug, name, description, sort_order, active)
				VALUES (:slug, :name, :description, coalesce(:sort, (SELECT coalesce(max(sort_order), 0) + 1 FROM %s)), :active)
				RETURNING id
				""".formatted(kind.table, kind.table))
			.param("slug", slug)
			.param("name", name)
			.param("description", Objects.requireNonNullElse(request.description(), "").trim())
			.param("sort", request.sortOrder())
			.param("active", !Boolean.FALSE.equals(request.active()))
			.query(Long.class)
			.single();
		audit.record(kind.name() + "_CREATED", kind.name(), id, Map.of("slug", slug, "name", name));
		return term(kind, id);
	}

	@Transactional
	public AdminTerm updateTerm(Vocabulary kind, Long id, TermRequest request) {
		AdminTerm before = term(kind, id);
		String name = request.name().trim();
		// the slug is referenced by templates, filters and links, so it never changes
		checkUniqueTerm(kind, name, before.slug(), id);
		jdbc.sql("UPDATE %s SET name = :name, description = :description, sort_order = :sort, active = :active WHERE id = :id"
			.formatted(kind.table))
			.param("name", name)
			.param("description", request.description() != null ? request.description().trim() : before.description())
			.param("sort", Objects.requireNonNullElse(request.sortOrder(), before.sortOrder()))
			.param("active", Objects.requireNonNullElse(request.active(), before.active()))
			.param("id", id)
			.update();
		audit.record(kind.name() + "_UPDATED", kind.name(), id, Map.of("slug", before.slug(), "name", name));
		return term(kind, id);
	}

	private void checkUniqueTerm(Vocabulary kind, String name, String slug, Long exceptId) {
		if (jdbc.sql("SELECT count(*) FROM %s WHERE (lower(name) = lower(:name) OR slug = :slug) AND id <> :id"
			.formatted(kind.table)).param("name", name).param("slug", slug).param("id", exceptId).query(Long.class).single() > 0) {
			throw ApiException.conflict("TERM_TAKEN", "A " + kind.name().toLowerCase() + " with this name already exists.")
				.with("field", "name");
		}
	}

	private AdminTerm term(Vocabulary kind, Long id) {
		return terms(kind).stream()
			.filter(t -> t.id().equals(id))
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("TERM_NOT_FOUND", "Not found."));
	}

	// ---------------------------------------------------------------- homepage rails

	@Transactional(readOnly = true)
	public List<HomeSection> homeSections(boolean activeOnly) {
		return jdbc.sql("SELECT * FROM home_sections" + (activeOnly ? " WHERE active" : "") + " ORDER BY position, id")
			.query((rs, n) -> new HomeSection(rs.getLong("id"), rs.getString("slug"), rs.getString("eyebrow"),
					rs.getString("title"), rs.getString("query"), rs.getInt("position"), rs.getBoolean("active"),
					BUILT_IN_SECTIONS.contains(rs.getString("slug"))))
			.list();
	}

	@Transactional
	public HomeSection createHomeSection(HomeSectionRequest request) {
		String slug = request.slug() != null ? request.slug() : ProductAdminService.slugify(request.title());
		if (jdbc.sql("SELECT count(*) FROM home_sections WHERE slug = :slug").param("slug", slug).query(Long.class).single() > 0) {
			throw ApiException.conflict("SECTION_SLUG_TAKEN", "A section with this slug already exists.").with("field", "slug");
		}
		Long id = jdbc.sql("""
				INSERT INTO home_sections (slug, eyebrow, title, query, position, active)
				VALUES (:slug, :eyebrow, :title, :query,
				        coalesce(:position, (SELECT coalesce(max(position), 0) + 1 FROM home_sections)), :active)
				RETURNING id
				""")
			.param("slug", slug)
			.param("eyebrow", Objects.requireNonNullElse(request.eyebrow(), "").trim())
			.param("title", request.title().trim())
			.param("query", normalizeQuery(request.query()))
			.param("position", request.position())
			.param("active", !Boolean.FALSE.equals(request.active()))
			.query(Long.class)
			.single();
		audit.record("HOME_SECTION_CREATED", "HOME_SECTION", id, Map.of("slug", slug));
		return homeSection(id);
	}

	@Transactional
	public HomeSection updateHomeSection(Long id, HomeSectionRequest request) {
		HomeSection before = homeSection(id);
		jdbc.sql("""
				UPDATE home_sections SET eyebrow = :eyebrow, title = :title, query = :query, position = :position,
				       active = :active, updated_at = now()
				WHERE id = :id
				""")
			.param("eyebrow", request.eyebrow() != null ? request.eyebrow().trim() : before.eyebrow())
			.param("title", request.title().trim())
			.param("query", normalizeQuery(request.query()))
			.param("position", Objects.requireNonNullElse(request.position(), before.position()))
			.param("active", Objects.requireNonNullElse(request.active(), before.active()))
			.param("id", id)
			.update();
		audit.record("HOME_SECTION_UPDATED", "HOME_SECTION", id, Map.of("slug", before.slug()));
		return homeSection(id);
	}

	@Transactional
	public void deleteHomeSection(Long id) {
		HomeSection section = homeSection(id);
		if (section.builtIn()) {
			throw ApiException.conflict("SECTION_BUILT_IN", "Built-in sections can be hidden but not deleted.");
		}
		jdbc.sql("DELETE FROM home_sections WHERE id = :id").param("id", id).update();
		audit.record("HOME_SECTION_DELETED", "HOME_SECTION", id, Map.of("slug", section.slug()));
	}

	/** Keeps only known catalog filter keys so a rail cannot carry arbitrary parameters. */
	static String normalizeQuery(String query) {
		String q = query.trim().replaceFirst("^\\?", "");
		StringBuilder result = new StringBuilder();
		for (String pair : q.split("&")) {
			if (pair.isBlank()) {
				continue;
			}
			String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
			if (!QUERY_KEYS.contains(key) || !pair.matches("[A-Za-z]+=[A-Za-z0-9 ,'&%._+-]{1,80}")) {
				throw ApiException.badRequest("INVALID_SECTION_QUERY",
						"Use catalog filters such as flag=trending, style=streetwear or gender=WOMEN.").with("field", "query");
			}
			if (!result.isEmpty()) {
				result.append('&');
			}
			result.append(pair);
		}
		if (result.isEmpty()) {
			throw ApiException.badRequest("INVALID_SECTION_QUERY", "Enter at least one catalog filter.").with("field", "query");
		}
		return result.toString();
	}

	private HomeSection homeSection(Long id) {
		return homeSections(false).stream()
			.filter(s -> s.id().equals(id))
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("SECTION_NOT_FOUND", "This homepage section does not exist."));
	}
}
