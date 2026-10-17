package com.buildmybundle.recommendation.engine;

import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.recommendation.engine.EngineModels.Need;
import com.buildmybundle.recommendation.engine.EngineModels.ScoringContext;
import com.buildmybundle.recommendation.engine.EngineModels.Tier;

/**
 * Product scoring, all components in [0, 1].
 *
 * <ul>
 * <li><b>Quality</b>: Bayesian-averaged rating (a product with 3 reviews cannot outrank one with 3,000 just by
 * luck) blended with the product's quality tier.</li>
 * <li><b>Relevance</b>: whether the product is made for the goal's purpose (context tag) and how many of the
 * customer's usage preferences it matches.</li>
 * <li><b>Brand</b>: 1 for a preferred brand.</li>
 * </ul>
 */
public final class Scoring {

	/** Ratings are shrunk towards this mean with the weight of this many reviews. */
	static final double PRIOR_MEAN = 4.0;
	static final double PRIOR_WEIGHT = 50;
	/** Optional categories count for less than required ones, so they are only added when worth it. */
	static final double OPTIONAL_WEIGHT = 0.6;

	private Scoring() {
	}

	public static long cents(ProductFacts product) {
		return product.product().price().movePointRight(2).longValueExact();
	}

	public static double bayesianRating(ProductFacts p) {
		double rating = p.product().ratingAvg().doubleValue();
		int count = p.product().ratingCount();
		return (PRIOR_WEIGHT * PRIOR_MEAN + rating * count) / (PRIOR_WEIGHT + count);
	}

	public static double quality(ProductFacts p) {
		double ratingNorm = clamp((bayesianRating(p) - 3.0) / 2.0);
		double tierNorm = (p.product().qualityTier() - 1) / 2.0;
		return 0.6 * ratingNorm + 0.4 * tierNorm;
	}

	public static double relevance(ProductFacts p, ScoringContext ctx) {
		double context = ctx.contextTag() == null ? 0.5 : (p.tags().contains(ctx.contextTag()) ? 1.0 : 0.0);
		double usage = ctx.usageTags().isEmpty() ? 0.5
				: ctx.usageTags().stream().filter(p.tags()::contains).count() / (double) ctx.usageTags().size();
		return 0.5 * context + 0.5 * usage;
	}

	public static double brand(ProductFacts p, ScoringContext ctx) {
		return ctx.preferredBrands().contains(p.product().brand()) ? 1.0 : 0.0;
	}

	public static double utility(ProductFacts p, Tier tier, ScoringContext ctx) {
		return tier.qualityWeight * quality(p) + tier.relevanceWeight * relevance(p, ctx)
				+ tier.brandWeight * brand(p, ctx);
	}

	/** Contribution of choosing {@code p} for {@code need} to a tier's objective. */
	public static double optionScore(Need need, ProductFacts p, long costCents, Tier tier, ScoringContext ctx,
			long budgetCents) {
		double weight = need.required() ? 1.0 : OPTIONAL_WEIGHT;
		return weight * utility(p, tier, ctx) - tier.costPenalty * costCents / (double) budgetCents;
	}

	private static double clamp(double value) {
		return Math.max(0, Math.min(1, value));
	}
}
