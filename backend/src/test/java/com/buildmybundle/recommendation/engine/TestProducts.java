package com.buildmybundle.recommendation.engine;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.compatibility.CompatibilityModels.Operator;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.compatibility.CompatibilityModels.Severity;

/** Builders for engine tests. */
final class TestProducts {

	private TestProducts() {
	}

	static Builder product(long id, String category, String price) {
		return new Builder(id, category, price);
	}

	static Rule rule(String code, String source, String target, Operator op, String sourceAttr, String targetAttr) {
		return new Rule(code, source, target, op, sourceAttr, targetAttr, null, Severity.BLOCK, code.toLowerCase(),
				code + " violated");
	}

	static Rule requires(String code, String source, String target, String attr, String unless) {
		return new Rule(code, source, target, Operator.REQUIRES_CATEGORY_UNLESS, attr, null, unless, Severity.BLOCK,
				"needs " + target, source + " needs " + target);
	}

	static final class Builder {

		private final long id;
		private final String category;
		private final String price;
		private double rating = 4.5;
		private int reviews = 100;
		private int tier = 2;
		private int stock = 10;
		private String brand = "Brand";
		private final Map<String, List<String>> attributes = new LinkedHashMap<>();
		private Set<String> tags = Set.of();

		Builder(long id, String category, String price) {
			this.id = id;
			this.category = category;
			this.price = price;
		}

		Builder rating(double rating, int reviews) {
			this.rating = rating;
			this.reviews = reviews;
			return this;
		}

		Builder tier(int tier) {
			this.tier = tier;
			return this;
		}

		Builder stock(int stock) {
			this.stock = stock;
			return this;
		}

		Builder brand(String brand) {
			this.brand = brand;
			return this;
		}

		Builder attr(String key, String... values) {
			attributes.put(key, List.of(values));
			return this;
		}

		Builder tags(String... tags) {
			this.tags = Set.of(tags);
			return this;
		}

		ProductFacts build() {
			ProductSummary summary = new ProductSummary(id, "SKU-" + id, "p-" + id, category + " " + id, brand,
					new BigDecimal(price), BigDecimal.valueOf(rating), reviews, tier, category, category, true, stock);
			return new ProductFacts(summary, (long) category.hashCode(), attributes, tags);
		}
	}
}
