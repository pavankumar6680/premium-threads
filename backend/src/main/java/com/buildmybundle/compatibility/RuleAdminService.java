package com.buildmybundle.compatibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.buildmybundle.catalog.CategoryInfo;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.compatibility.CompatibilityModels.Operator;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.compatibility.CompatibilityModels.Severity;
import com.buildmybundle.shared.audit.AuditLog;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compatibility rule administration. Rules are data, so a new rule takes effect on the next check or
 * recommendation without a deployment. Because a wrong rule can block large parts of the catalog, rules are
 * validated against the categories' attribute definitions, and {@link #preview} shows how many active product
 * pairs a rule would reject before it is saved.
 */
@Service
public class RuleAdminService {

	private static final int MAX_EXAMPLES = 5;

	public record AdminRule(Long id, String code, String sourceCategory, String targetCategory, Operator operator,
			String sourceAttr, String targetAttr, String value, Severity severity, String label, String message,
			boolean active) {
	}

	public record RuleRequest(
			@NotBlank @Size(max = 60) @Pattern(regexp = "[A-Z0-9_]{3,60}", message = "UPPER_SNAKE_CASE, 3-60 characters") String code,
			@NotBlank String sourceCategory, String targetCategory, @NotNull Operator operator,
			@NotBlank @Size(max = 60) String sourceAttr, @Size(max = 60) String targetAttr,
			@Size(max = 60) String value, @NotNull Severity severity, @NotBlank @Size(max = 120) String label,
			@NotBlank @Size(max = 300) String message, @NotNull Boolean active) {
	}

	/**
	 * Effect of a rule on the active catalog. For pairwise rules: source × target product pairs. For
	 * "requires category" rules: source products that would need a companion. Spatial rules depend on the
	 * customer's space, so only the number of products carrying the attribute is reported.
	 */
	public record RuleImpact(int evaluated, int violating, int notApplicable, List<String> examples, String summary) {
	}

	private final JdbcClient jdbc;
	private final ProductCatalog catalog;
	private final AuditLog audit;

	public RuleAdminService(JdbcClient jdbc, ProductCatalog catalog, AuditLog audit) {
		this.jdbc = jdbc;
		this.catalog = catalog;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<AdminRule> list() {
		return jdbc.sql("""
				SELECT r.id, r.code, s.slug AS source_slug, t.slug AS target_slug, r.operator, r.source_attr,
				       r.target_attr, r.rule_value, r.severity, r.label, r.message, r.active
				FROM compatibility_rules r
				JOIN categories s ON s.id = r.source_category_id
				LEFT JOIN categories t ON t.id = r.target_category_id
				ORDER BY r.id
				""")
			.query((rs, n) -> new AdminRule(rs.getLong("id"), rs.getString("code"), rs.getString("source_slug"),
					rs.getString("target_slug"), Operator.valueOf(rs.getString("operator")), rs.getString("source_attr"),
					rs.getString("target_attr"), rs.getString("rule_value"), Severity.valueOf(rs.getString("severity")),
					rs.getString("label"), rs.getString("message"), rs.getBoolean("active")))
			.list();
	}

	@Transactional
	public AdminRule create(RuleRequest request) {
		Validated v = validate(request);
		if (jdbc.sql("SELECT count(*) FROM compatibility_rules WHERE code = :code")
			.param("code", request.code())
			.query(Long.class)
			.single() > 0) {
			throw ApiException.conflict("RULE_CODE_TAKEN", "A rule with this code already exists.").with("field", "code");
		}
		Long id = jdbc.sql("""
				INSERT INTO compatibility_rules (code, source_category_id, target_category_id, operator, source_attr,
				       target_attr, rule_value, severity, label, message, active)
				VALUES (:code, :source, :target, :operator, :sourceAttr, :targetAttr, :value, :severity, :label,
				        :message, :active)
				RETURNING id
				""").paramSource(params(request, v)).query(Long.class).single();
		audit.record("RULE_CREATED", "COMPATIBILITY_RULE", id,
				Map.of("code", request.code(), "operator", request.operator().name(), "active", request.active()));
		return find(id);
	}

	@Transactional
	public AdminRule update(Long id, RuleRequest request) {
		AdminRule before = find(id);
		Validated v = validate(request);
		if (!before.code().equals(request.code()) && jdbc.sql("SELECT count(*) FROM compatibility_rules WHERE code = :code")
			.param("code", request.code())
			.query(Long.class)
			.single() > 0) {
			throw ApiException.conflict("RULE_CODE_TAKEN", "A rule with this code already exists.").with("field", "code");
		}
		Map<String, Object> params = params(request, v);
		params.put("id", id);
		jdbc.sql("""
				UPDATE compatibility_rules SET code = :code, source_category_id = :source, target_category_id = :target,
				       operator = :operator, source_attr = :sourceAttr, target_attr = :targetAttr, rule_value = :value,
				       severity = :severity, label = :label, message = :message, active = :active
				WHERE id = :id
				""").paramSource(params).update();
		audit.record("RULE_UPDATED", "COMPATIBILITY_RULE", id, Map.of("code", request.code(), "activeBefore",
				before.active(), "active", request.active(), "severity", request.severity().name()));
		return find(id);
	}

	@Transactional
	public AdminRule setActive(Long id, boolean active) {
		AdminRule before = find(id);
		if (before.active() != active) {
			jdbc.sql("UPDATE compatibility_rules SET active = :active WHERE id = :id")
				.param("active", active)
				.param("id", id)
				.update();
			audit.record(active ? "RULE_ACTIVATED" : "RULE_DEACTIVATED", "COMPATIBILITY_RULE", id,
					Map.of("code", before.code()));
		}
		return find(id);
	}

	/** Evaluates a (possibly unsaved) rule against every active product it applies to. */
	@Transactional(readOnly = true)
	public RuleImpact preview(RuleRequest request) {
		Validated v = validate(request);
		Rule rule = new Rule(request.code(), request.sourceCategory(), request.targetCategory(), request.operator(),
				request.sourceAttr(), blankToNull(request.targetAttr()), blankToNull(request.value()), request.severity(),
				request.label(), request.message());
		List<ProductFacts> sources = catalog.activeFactsInCategories(List.of(v.sourceId()));
		if (request.operator().pairwise()) {
			List<ProductFacts> targets = catalog.activeFactsInCategories(List.of(v.targetId()));
			int violating = 0;
			int notApplicable = 0;
			List<String> examples = new ArrayList<>();
			for (ProductFacts s : sources) {
				for (ProductFacts t : targets) {
					Optional<Boolean> ok = CompatibilityChecker.evaluatePair(rule, s, t);
					if (ok.isEmpty()) {
						notApplicable++;
					}
					else if (!ok.get()) {
						violating++;
						if (examples.size() < MAX_EXAMPLES) {
							examples.add(s.product().name() + " × " + t.product().name());
						}
					}
				}
			}
			int evaluated = sources.size() * targets.size();
			return new RuleImpact(evaluated, violating, notApplicable, examples,
					"%d of %d product pairs would be %s; %d pairs lack the attributes and are not affected."
						.formatted(violating, evaluated, request.severity() == Severity.BLOCK ? "blocked" : "flagged",
								notApplicable));
		}
		if (request.operator() == Operator.REQUIRES_CATEGORY_UNLESS) {
			List<ProductFacts> needing = sources.stream()
				.filter(s -> !s.values(request.sourceAttr()).isEmpty())
				.filter(s -> !CompatibilityChecker.selfSufficient(rule, s))
				.toList();
			int notApplicable = (int) sources.stream().filter(s -> s.values(request.sourceAttr()).isEmpty()).count();
			return new RuleImpact(sources.size(), needing.size(), notApplicable,
					needing.stream().limit(MAX_EXAMPLES).map(s -> s.product().name()).toList(),
					"%d of %d products would need a product from '%s' in the same bundle."
						.formatted(needing.size(), sources.size(), request.targetCategory()));
		}
		int withAttribute = (int) sources.stream().filter(s -> s.number(request.sourceAttr()).isPresent()).count();
		return new RuleImpact(sources.size(), 0, sources.size() - withAttribute, List.of(),
				"%d of %d products have '%s' and will be checked against each customer's space."
					.formatted(withAttribute, sources.size(), request.sourceAttr()));
	}

	// ---------------------------------------------------------------- validation

	private record Validated(Long sourceId, Long targetId) {
	}

	private Validated validate(RuleRequest r) {
		Map<String, Long> leaves = catalog.categories()
			.stream()
			.filter(CategoryInfo::leaf)
			.collect(Collectors.toMap(CategoryInfo::slug, CategoryInfo::id));
		Map<String, Set<String>> keys = catalog.attributeKeysByCategory();
		Long sourceId = leaves.get(r.sourceCategory());
		if (sourceId == null) {
			throw invalid("sourceCategory", "Choose an existing product category.");
		}
		if (!keys.getOrDefault(r.sourceCategory(), Set.of()).contains(r.sourceAttr())) {
			throw invalid("sourceAttr", "'" + r.sourceAttr() + "' is not an attribute of " + r.sourceCategory() + ".");
		}
		Long targetId = null;
		boolean needsTarget = r.operator().pairwise() || r.operator() == Operator.REQUIRES_CATEGORY_UNLESS;
		if (needsTarget) {
			targetId = r.targetCategory() == null ? null : leaves.get(r.targetCategory());
			if (targetId == null) {
				throw invalid("targetCategory", "This kind of rule needs a target category.");
			}
		}
		else if (r.targetCategory() != null && !r.targetCategory().isBlank()) {
			throw invalid("targetCategory", "Space rules compare a product with the customer's room, not another category.");
		}
		if (r.operator().pairwise()) {
			if (r.targetAttr() == null || !keys.getOrDefault(r.targetCategory(), Set.of()).contains(r.targetAttr())) {
				throw invalid("targetAttr", "Choose an attribute of " + r.targetCategory() + ".");
			}
		}
		if (r.operator() == Operator.REQUIRES_CATEGORY_UNLESS && (r.value() == null || r.value().isBlank())) {
			throw invalid("value", "Enter the value that makes the companion product unnecessary (e.g. USB).");
		}
		return new Validated(sourceId, targetId);
	}

	private static Map<String, Object> params(RuleRequest r, Validated v) {
		Map<String, Object> params = new java.util.HashMap<>();
		params.put("code", r.code());
		params.put("source", v.sourceId());
		params.put("target", v.targetId());
		params.put("operator", r.operator().name());
		params.put("sourceAttr", r.sourceAttr());
		params.put("targetAttr", r.operator().pairwise() ? r.targetAttr() : null);
		params.put("value", r.operator() == Operator.REQUIRES_CATEGORY_UNLESS ? r.value().trim() : null);
		params.put("severity", r.severity().name());
		params.put("label", r.label().trim());
		params.put("message", r.message().trim());
		params.put("active", r.active());
		return params;
	}

	private AdminRule find(Long id) {
		return list().stream()
			.filter(r -> r.id().equals(id))
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("RULE_NOT_FOUND", "Compatibility rule not found."));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static ApiException invalid(String field, String message) {
		return ApiException.badRequest("INVALID_RULE", message).with("field", field);
	}
}
