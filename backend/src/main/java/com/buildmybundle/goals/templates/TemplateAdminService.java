package com.buildmybundle.goals.templates;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.buildmybundle.catalog.CategoryInfo;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.goals.templates.GoalTemplates.Template;
import com.buildmybundle.shared.audit.AuditLog;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Goal template administration. Templates drive the wizard, the goal interpreter (keywords) and the
 * recommendation engine (items, usage tags, context tag), so changes apply to the next goal without a deploy.
 * Existing goals are immutable snapshots and are not affected.
 */
@Service
public class TemplateAdminService {

	private static final String SLUG = "[a-z0-9]+(?:-[a-z0-9]+)*";

	public record AdminTemplateItem(String categorySlug, String categoryName, boolean required,
			boolean selectedByDefault, int quantity, String note) {
	}

	public record AdminTemplate(Long id, String slug, String title, String tagline, String description,
			BigDecimal defaultBudget, String contextTag, boolean active, List<AdminTemplateItem> items,
			List<GoalTemplates.UsageOption> usageOptions, List<String> keywords) {
	}

	public record ItemRequest(@NotBlank String categorySlug, @NotNull Boolean required,
			@NotNull Boolean selectedByDefault, @NotNull @Min(1) @Max(10) Integer quantity,
			@Size(max = 200) String note) {
	}

	public record UsageRequest(@NotBlank @Size(max = 40) @Pattern(regexp = SLUG) String tag,
			@NotBlank @Size(max = 80) String label) {
	}

	public record TemplateRequest(@NotBlank @Size(max = 60) @Pattern(regexp = SLUG, message = "lowercase words joined by dashes") String slug,
			@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 200) String tagline,
			@Size(max = 2000) String description,
			@NotNull @DecimalMin("1.00") @DecimalMax("1000000.00") BigDecimal defaultBudget,
			@Size(max = 40) @Pattern(regexp = SLUG) String contextTag, @NotNull Boolean active,
			@NotEmpty @Size(max = 20) List<@Valid ItemRequest> items,
			@Size(max = 10) List<@Valid UsageRequest> usageOptions,
			@Size(max = 30) List<@NotBlank @Size(max = 60) String> keywords) {
	}

	private final JdbcClient jdbc;
	private final GoalTemplates templates;
	private final ProductCatalog catalog;
	private final AuditLog audit;

	public TemplateAdminService(JdbcClient jdbc, GoalTemplates templates, ProductCatalog catalog, AuditLog audit) {
		this.jdbc = jdbc;
		this.templates = templates;
		this.catalog = catalog;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<AdminTemplate> list() {
		Map<Long, CategoryInfo> categories = catalog.categories()
			.stream()
			.collect(Collectors.toMap(CategoryInfo::id, c -> c));
		Set<Long> active = new HashSet<>(
				jdbc.sql("SELECT id FROM goal_templates WHERE active").query(Long.class).list());
		return templates.findAll().stream().map(t -> view(t, active.contains(t.id()), categories)).toList();
	}

	@Transactional
	public AdminTemplate create(TemplateRequest request) {
		Map<String, Long> leaves = validate(request);
		if (exists(request.slug(), null)) {
			throw ApiException.conflict("TEMPLATE_SLUG_TAKEN", "A template with this slug already exists.")
				.with("field", "slug");
		}
		Long id = jdbc.sql("""
				INSERT INTO goal_templates (slug, title, tagline, description, default_budget, context_tag, active, sort_order)
				VALUES (:slug, :title, :tagline, :description, :budget, :contextTag, :active,
				        (SELECT coalesce(max(sort_order), 0) + 1 FROM goal_templates))
				RETURNING id
				""").paramSource(params(request)).query(Long.class).single();
		writeChildren(id, request, leaves);
		audit.record("TEMPLATE_CREATED", "GOAL_TEMPLATE", id, Map.of("slug", request.slug(), "active", request.active()));
		return find(id);
	}

	@Transactional
	public AdminTemplate update(Long id, TemplateRequest request) {
		find(id);
		Map<String, Long> leaves = validate(request);
		if (exists(request.slug(), id)) {
			throw ApiException.conflict("TEMPLATE_SLUG_TAKEN", "A template with this slug already exists.")
				.with("field", "slug");
		}
		Map<String, Object> params = params(request);
		params.put("id", id);
		jdbc.sql("""
				UPDATE goal_templates SET slug = :slug, title = :title, tagline = :tagline, description = :description,
				       default_budget = :budget, context_tag = :contextTag, active = :active
				WHERE id = :id
				""").paramSource(params).update();
		for (String table : List.of("goal_template_items", "goal_template_usage_options", "goal_template_keywords")) {
			jdbc.sql("DELETE FROM " + table + " WHERE template_id = :id").param("id", id).update();
		}
		writeChildren(id, request, leaves);
		audit.record("TEMPLATE_UPDATED", "GOAL_TEMPLATE", id,
				Map.of("slug", request.slug(), "active", request.active(), "items", request.items().size()));
		return find(id);
	}

	private Map<String, Long> validate(TemplateRequest request) {
		Map<String, Long> leaves = catalog.categories()
			.stream()
			.filter(CategoryInfo::leaf)
			.collect(Collectors.toMap(CategoryInfo::slug, CategoryInfo::id));
		Set<String> seen = new HashSet<>();
		for (ItemRequest item : request.items()) {
			if (!leaves.containsKey(item.categorySlug())) {
				throw ApiException.badRequest("UNKNOWN_CATEGORY", "Unknown category: " + item.categorySlug())
					.with("field", "items");
			}
			if (!seen.add(item.categorySlug())) {
				throw ApiException.badRequest("DUPLICATE_ITEM", item.categorySlug() + " is listed twice.")
					.with("field", "items");
			}
		}
		if (request.items().stream().noneMatch(ItemRequest::required)) {
			throw ApiException.badRequest("NO_REQUIRED_ITEM", "A template needs at least one required item.")
				.with("field", "items");
		}
		return leaves;
	}

	private void writeChildren(Long id, TemplateRequest request, Map<String, Long> leaves) {
		int position = 0;
		for (ItemRequest item : request.items()) {
			jdbc.sql("""
					INSERT INTO goal_template_items (template_id, category_id, required, selected_by_default, quantity, note, sort_order)
					VALUES (:id, :category, :required, :selected, :quantity, :note, :position)
					""")
				.param("id", id)
				.param("category", leaves.get(item.categorySlug()))
				.param("required", item.required())
				// a required item is always selected (also enforced by a CHECK constraint)
				.param("selected", item.required() || item.selectedByDefault())
				.param("quantity", item.quantity())
				.param("note", item.note() == null || item.note().isBlank() ? null : item.note().trim(), java.sql.Types.VARCHAR)
				.param("position", position++)
				.update();
		}
		position = 0;
		Set<String> tags = new HashSet<>();
		for (UsageRequest usage : request.usageOptions() == null ? List.<UsageRequest>of() : request.usageOptions()) {
			if (tags.add(usage.tag())) {
				jdbc.sql("""
						INSERT INTO goal_template_usage_options (template_id, tag, label, sort_order)
						VALUES (:id, :tag, :label, :position)
						""").param("id", id).param("tag", usage.tag()).param("label", usage.label().trim())
					.param("position", position++)
					.update();
			}
		}
		Set<String> keywords = new LinkedHashSet<>();
		(request.keywords() == null ? List.<String>of() : request.keywords())
			.forEach(k -> keywords.add(k.trim().toLowerCase(Locale.ROOT)));
		keywords.forEach(k -> jdbc.sql("INSERT INTO goal_template_keywords (template_id, keyword) VALUES (:id, :keyword)")
			.param("id", id)
			.param("keyword", k)
			.update());
	}

	private static Map<String, Object> params(TemplateRequest r) {
		Map<String, Object> params = new java.util.HashMap<>();
		params.put("slug", r.slug());
		params.put("title", r.title().trim());
		params.put("tagline", r.tagline().trim());
		params.put("description", r.description() == null ? "" : r.description().trim());
		params.put("budget", r.defaultBudget());
		params.put("contextTag", r.contextTag() == null || r.contextTag().isBlank() ? null : r.contextTag());
		params.put("active", r.active());
		return params;
	}

	private boolean exists(String slug, Long exceptId) {
		return jdbc.sql("SELECT count(*) FROM goal_templates WHERE slug = :slug AND id <> :id")
			.param("slug", slug)
			.param("id", exceptId == null ? -1L : exceptId)
			.query(Long.class)
			.single() > 0;
	}

	private AdminTemplate find(Long id) {
		return list().stream()
			.filter(t -> t.id().equals(id))
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("TEMPLATE_NOT_FOUND", "Goal template not found."));
	}

	private static AdminTemplate view(Template t, boolean active, Map<Long, CategoryInfo> categories) {
		List<AdminTemplateItem> items = t.items()
			.stream()
			.map(i -> {
				CategoryInfo c = categories.get(i.categoryId());
				return new AdminTemplateItem(c == null ? null : c.slug(), c == null ? null : c.name(), i.required(),
						i.selectedByDefault(), i.quantity(), i.note());
			})
			.toList();
		return new AdminTemplate(t.id(), t.slug(), t.title(), t.tagline(), t.description(), t.defaultBudget(),
				t.contextTag(), active, items, t.usageOptions(), t.keywords());
	}
}
