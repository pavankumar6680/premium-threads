package com.buildmybundle.compatibility;

import java.math.BigDecimal;
import java.util.List;

/** Public types of the compatibility module. */
public final class CompatibilityModels {

	private CompatibilityModels() {
	}

	public enum Operator {

		EQUALS, LIST_CONTAINS, LIST_INTERSECTS, LTE, LTE_SPACE_WIDTH, LTE_SPACE_DEPTH, REQUIRES_CATEGORY_UNLESS;

		/** Compares a product with a product of another category. */
		public boolean pairwise() {
			return this == EQUALS || this == LIST_CONTAINS || this == LIST_INTERSECTS || this == LTE;
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

		public boolean involves(String categorySlug) {
			return categorySlug.equals(sourceCategory) || categorySlug.equals(targetCategory);
		}

		/** Attribute of {@code categorySlug} that this rule reads, or null. */
		public String attributeFor(String categorySlug) {
			if (categorySlug.equals(sourceCategory)) {
				return sourceAttr;
			}
			if (categorySlug.equals(targetCategory)) {
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
