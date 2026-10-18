package com.buildmybundle.compatibility;

import java.util.Collection;
import java.util.List;

import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.compatibility.CompatibilityModels.Operator;
import com.buildmybundle.compatibility.CompatibilityModels.Report;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.compatibility.CompatibilityModels.Severity;
import com.buildmybundle.compatibility.CompatibilityModels.Space;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads the active compatibility rules and checks product sets against them. */
@Service
@Transactional(readOnly = true)
public class CompatibilityService {

	private final JdbcClient jdbc;
	private final ProductCatalog catalog;

	public CompatibilityService(JdbcClient jdbc, ProductCatalog catalog) {
		this.jdbc = jdbc;
		this.catalog = catalog;
	}

	public List<Rule> activeRules() {
		return jdbc.sql("""
				SELECT r.code, coalesce(s.slug, '*') AS source_slug, t.slug AS target_slug, r.operator, r.source_attr, r.target_attr,
				       r.rule_value, r.severity, r.label, r.message
				FROM compatibility_rules r
				LEFT JOIN categories s ON s.id = r.source_category_id
				LEFT JOIN categories t ON t.id = r.target_category_id
				WHERE r.active
				ORDER BY r.id
				""")
			.query((rs, n) -> {
				Operator operator = Operator.valueOf(rs.getString("operator"));
				// a pairwise rule without a target category applies to every category
				String target = rs.getString("target_slug") == null && operator.pairwise() ? Rule.ANY : rs.getString("target_slug");
				return new Rule(rs.getString("code"), rs.getString("source_slug"), target, operator, rs.getString("source_attr"),
						rs.getString("target_attr"), rs.getString("rule_value"), Severity.valueOf(rs.getString("severity")),
						rs.getString("label"), rs.getString("message"));
			})
			.list();
	}

	/** Checks a set of products (e.g. a bundle) against every active rule. */
	public Report check(Collection<Long> productIds, Space space) {
		return CompatibilityChecker.report(catalog.facts(productIds), space == null ? Space.UNKNOWN : space, activeRules());
	}
}
