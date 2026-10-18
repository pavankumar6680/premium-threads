package com.buildmybundle.compatibility;

import java.math.BigDecimal;
import java.util.List;

/** Public types of the compatibility module. */
public final class CompatibilityModels {

	private CompatibilityModels() {
	}

	public enum Operator {

		EQUALS, LIST_CONTAINS, LIST_INTERSECTS, LIST_DISJOINT, LTE, MAX_DIFFERENCE, LTE_SPACE_WIDTH, LTE_SPACE_DEPTH,
		REQUIRES_CATEGORY_UNLESS;

		/** Compares a product with another product of the bundle. */
		public boolean pairwise() {
			return this == EQUALS || this == LIST_CONTAINS || this == LIST_INTERSECTS || this == LIST_DISJOINT
					|| this == LTE || this == MAX_DIFFERENCE;
		}

		/** Compares a product with the customer's available space. */
		public boolean spatial() {
			return this == LTE_SPACE_WIDTH || this == LTE_SPACE_DEPTH;
		}
	}

	public enum Severity {
		/** Never recommend or sell as compatible. */
		BLOCK,
		/** Allowed, but shown as a warning. */
		WARN
	}

	/**
	 * @param label short phrase describing a satisfied rule ("within the arm's load limit")
	 * @param message explanation shown when the rule is violated
	 */
	public record Rule(String code, String sourceCategory, String targetCategory, Operator operator, String sourceAttr,
			String targetAttr, String value, Severity severity, String label, String message) {

		/** Category placeholder of rules that apply to every category (e.g. "same wardrobe"). */
		public static final String ANY = "*";

		/** True when a rule category ({@link #ANY} or a slug) covers a product category. */
		public static boolean matchesCategory(String ruleCategory, String categorySlug) {
			return ruleCategory != null && (ANY.equals(ruleCategory) || ruleCategory.equals(categorySlug));
		}

		/** Applies between every two pieces, whatever their categories. */
		public boolean universal() {
			return ANY.equals(sourceCategory) && ANY.equals(targetCategory);
		}

		public boolean involves(String categorySlug) {
			return matchesCategory(sourceCategory, categorySlug) || matchesCategory(targetCategory, categorySlug);
		}

		/** Attribute of {@code categorySlug} that this rule reads, or null. */
		public String attributeFor(String categorySlug) {
			if (matchesCategory(sourceCategory, categorySlug)) {
				return sourceAttr;
			}
			if (matchesCategory(targetCategory, categorySlug)) {
				return targetAttr;
			}
			return null;
		}
	}

	/** The customer's available floor space; either dimension may be unknown (null). */
	public record Space(BigDecimal widthCm, BigDecimal depthCm) {

		public static final Space UNKNOWN = new Space(null, null);
	}

	public record Violation(String ruleCode, Severity severity, String message, List<Long> productIds) {
	}

	/** A rule that was evaluated and satisfied. */
	public record Check(String ruleCode, String label, List<Long> productIds) {
	}

	/** @param compatible true when no BLOCK rule is violated */
	public record Report(boolean compatible, List<Check> passed, List<Violation> violations) {
	}
}
