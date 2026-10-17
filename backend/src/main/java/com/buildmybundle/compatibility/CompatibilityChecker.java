package com.buildmybundle.compatibility;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.compatibility.CompatibilityModels.Check;
import com.buildmybundle.compatibility.CompatibilityModels.Operator;
import com.buildmybundle.compatibility.CompatibilityModels.Report;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.compatibility.CompatibilityModels.Severity;
import com.buildmybundle.compatibility.CompatibilityModels.Space;
import com.buildmybundle.compatibility.CompatibilityModels.Violation;

/**
 * Evaluates compatibility rules against structured product specifications; a pure function of its inputs.
 *
 * <p>
 * A rule whose attribute is missing on a product is <em>not applicable</em> rather than violated: the
 * catalog cannot prove an incompatibility it has no data for. Such rules are neither passed nor failed.
 */
public final class CompatibilityChecker {

	private CompatibilityChecker() {
	}

	/** Outcome of one rule: empty when not applicable. */
	public static Optional<Boolean> evaluatePair(Rule rule, ProductFacts source, ProductFacts target) {
		List<String> s = source.values(rule.sourceAttr());
		List<String> t = target.values(rule.targetAttr());
		if (s.isEmpty() || t.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(switch (rule.operator()) {
			case EQUALS -> s.stream().anyMatch(t::contains);
			case LIST_CONTAINS -> t.containsAll(s);
			case LIST_INTERSECTS -> s.stream().anyMatch(t::contains);
			case LTE -> {
				Optional<Double> a = source.number(rule.sourceAttr());
				Optional<Double> b = target.number(rule.targetAttr());
				yield a.isEmpty() || b.isEmpty() || a.get() <= b.get();
			}
			default -> throw new IllegalArgumentException("Not a pairwise operator: " + rule.operator());
		});
	}

	/** Outcome of a space rule: empty when the product or the customer gave no dimension. */
	public static Optional<Boolean> evaluateSpace(Rule rule, ProductFacts product, Space space) {
		BigDecimal limit = rule.operator() == Operator.LTE_SPACE_WIDTH ? space.widthCm() : space.depthCm();
		Optional<Double> value = product.number(rule.sourceAttr());
		if (limit == null || value.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(value.get() <= limit.doubleValue());
	}

	/** True when a product does not need another category for this presence rule (e.g. a USB microphone). */
	public static boolean selfSufficient(Rule rule, ProductFacts product) {
		return product.values(rule.sourceAttr()).contains(rule.value());
	}

	/** BLOCK and WARN violations between two products of different categories (rules in either direction). */
	public static List<Violation> pairViolations(ProductFacts a, ProductFacts b, List<Rule> rules) {
		List<Violation> violations = new ArrayList<>();
		for (Rule rule : rules) {
			if (!rule.operator().pairwise()) {
				continue;
			}
			ProductFacts source = matches(rule.sourceCategory(), a) ? a : matches(rule.sourceCategory(), b) ? b : null;
			ProductFacts target = source == a ? b : a;
			if (source == null || !matches(rule.targetCategory(), target)) {
				continue;
			}
			if (evaluatePair(rule, source, target).filter(ok -> !ok).isPresent()) {
				violations.add(new Violation(rule.code(), rule.severity(), rule.message(), List.of(source.id(), target.id())));
			}
		}
		return violations;
	}

	/** True when no BLOCK rule is violated between the two products. */
	public static boolean pairCompatible(ProductFacts a, ProductFacts b, List<Rule> rules) {
		return pairViolations(a, b, rules).stream().noneMatch(v -> v.severity() == Severity.BLOCK);
	}

	public static List<Violation> spaceViolations(ProductFacts product, Space space, List<Rule> rules) {
		List<Violation> violations = new ArrayList<>();
		for (Rule rule : rules) {
			if (rule.operator().spatial() && matches(rule.sourceCategory(), product)
					&& evaluateSpace(rule, product, space).filter(ok -> !ok).isPresent()) {
				violations.add(new Violation(rule.code(), rule.severity(), rule.message(), List.of(product.id())));
			}
		}
		return violations;
	}

	public static boolean fitsSpace(ProductFacts product, Space space, List<Rule> rules) {
		return spaceViolations(product, space, rules).stream().noneMatch(v -> v.severity() == Severity.BLOCK);
	}

	/** Presence rules over a whole set of products ("an XLR-only microphone needs an audio interface"). */
	public static List<Violation> presenceViolations(List<ProductFacts> products, List<Rule> rules) {
		List<Violation> violations = new ArrayList<>();
		for (Rule rule : rules) {
			if (rule.operator() != Operator.REQUIRES_CATEGORY_UNLESS) {
				continue;
			}
			boolean targetPresent = products.stream().anyMatch(p -> matches(rule.targetCategory(), p));
			for (ProductFacts p : products) {
				if (matches(rule.sourceCategory(), p) && !selfSufficient(rule, p) && !targetPresent) {
					violations.add(new Violation(rule.code(), rule.severity(), rule.message(), List.of(p.id())));
				}
			}
		}
		return violations;
	}

	/** Full report for a set of products, listing satisfied checks as well as violations. */
	public static Report report(List<ProductFacts> products, Space space, List<Rule> rules) {
		List<Check> passed = new ArrayList<>();
		List<Violation> violations = new ArrayList<>();
		for (Rule rule : rules) {
			switch (rule.operator()) {
				case LTE_SPACE_WIDTH, LTE_SPACE_DEPTH -> products.stream()
					.filter(p -> matches(rule.sourceCategory(), p))
					.forEach(p -> evaluateSpace(rule, p, space).ifPresent(ok -> record(ok, rule, List.of(p.id()), passed, violations)));
				case REQUIRES_CATEGORY_UNLESS -> {
					boolean targetPresent = products.stream().anyMatch(p -> matches(rule.targetCategory(), p));
					products.stream()
						.filter(p -> matches(rule.sourceCategory(), p) && !selfSufficient(rule, p))
						.forEach(p -> record(targetPresent, rule, List.of(p.id()), passed, violations));
				}
				default -> {
					for (ProductFacts source : products) {
						for (ProductFacts target : products) {
							if (matches(rule.sourceCategory(), source) && matches(rule.targetCategory(), target)) {
								evaluatePair(rule, source, target).ifPresent(
										ok -> record(ok, rule, List.of(source.id(), target.id()), passed, violations));
							}
						}
					}
				}
			}
		}
		boolean compatible = violations.stream().noneMatch(v -> v.severity() == Severity.BLOCK);
		return new Report(compatible, passed, violations);
	}

	private static void record(boolean ok, Rule rule, List<Long> ids, List<Check> passed, List<Violation> violations) {
		if (ok) {
			passed.add(new Check(rule.code(), rule.label(), ids));
		}
		else {
			violations.add(new Violation(rule.code(), rule.severity(), rule.message(), ids));
		}
	}

	private static boolean matches(String categorySlug, ProductFacts product) {
		return categorySlug != null && categorySlug.equals(product.categorySlug());
	}
}
