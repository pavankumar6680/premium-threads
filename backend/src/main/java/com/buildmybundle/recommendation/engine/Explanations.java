package com.buildmybundle.recommendation.engine;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.compatibility.CompatibilityChecker;
import com.buildmybundle.compatibility.CompatibilityModels.Operator;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.recommendation.engine.EngineModels.ScoringContext;
import com.buildmybundle.recommendation.engine.EngineModels.OwnedCategory;
import com.buildmybundle.recommendation.engine.EngineModels.Pick;
import com.buildmybundle.recommendation.engine.EngineModels.Problem;
import com.buildmybundle.recommendation.engine.EngineModels.Solution;
import com.buildmybundle.recommendation.engine.EngineModels.Tier;

/**
 * Deterministic, fact-based explanations: every sentence is derived from catalog data (price rank, rating,
 * tags, brand) and from the compatibility rules the product satisfies. Nothing is invented; phase 6 may
 * reword these facts with a language model.
 */
final class Explanations {

	private static final int MAX_COMPATIBILITY_REASONS = 2;

	private Explanations() {
	}

	static Map<Long, String> forSolution(Problem problem, Tier tier, Solution solution) {
		Map<Long, String> explanations = new LinkedHashMap<>();
		List<ProductFacts> chosen = solution.chosen().stream().map(Pick::product).toList();
		for (Pick pick : solution.chosen()) {
			explanations.put(pick.product().id(), explain(problem, tier, pick, chosen));
		}
		return explanations;
	}

	private static String explain(Problem problem, Tier tier, Pick pick, List<ProductFacts> chosen) {
		ProductFacts product = pick.product();
		String category = pick.need().categoryName();
		// superlatives ("lowest-priced", "highest-rated") are claimed against every product that could be bought,
		// not just the subset the tier searched, so they are always literally true
		List<ProductFacts> peers = problem.candidates()
			.getOrDefault(pick.need().categorySlug(), List.of())
			.stream()
			.filter(p -> BundleOptimizer.eligible(p, pick.need(), problem))
			.toList();
		List<String> reasons = new ArrayList<>();

		long price = Scoring.cents(product);
		boolean cheapest = peers.stream().allMatch(p -> Scoring.cents(p) >= price);
		boolean bestRated = peers.stream().allMatch(p -> Scoring.quality(p) <= Scoring.quality(product) + 1e-9);
		if (cheapest && peers.size() > 1) {
			reasons.add("the lowest-priced option in " + category);
		}
		else if (bestRated && peers.size() > 1) {
			reasons.add("the highest-rated option in " + category);
		}
		else {
			reasons.add(switch (tier) {
				case BUDGET -> "a low-cost choice in " + category;
				case BALANCED -> "the best balance of quality and price in " + category;
				case PREMIUM -> "the top-quality choice in " + category + " that fits your budget";
			});
		}

		reasons.add("rated %.1f/5 by %,d customers".formatted(product.product().ratingAvg().doubleValue(),
				product.product().ratingCount()));

		Set<String> fits = new LinkedHashSet<>();
		String context = problem.context().contextTag();
		if (context != null && product.tags().contains(context)) {
			fits.add(humanize(context));
		}
		problem.context()
			.usageTags()
			.stream()
			.filter(t -> !ScoringContext.PREFERRED_COLOR_TAG.equals(t) && product.tags().contains(t))
			.map(Explanations::humanize)
			.forEach(fits::add);
		if (!fits.isEmpty()) {
			reasons.add("made for " + String.join(" and ", fits));
		}
		if (product.tags().contains(ScoringContext.PREFERRED_COLOR_TAG)) {
			reasons.add("comes in a color you picked");
		}
		if (problem.context().preferredBrands().contains(product.product().brand())) {
			reasons.add("from your preferred brand " + product.product().brand());
		}

		int compatibilityReasons = 0;
		for (Rule rule : problem.rules()) {
			if (compatibilityReasons >= MAX_COMPATIBILITY_REASONS) {
				break;
			}
			if (rule.operator().pairwise() && !rule.universal()) {
				for (ProductFacts other : chosen) {
					if (other == product || compatibilityReasons >= MAX_COMPATIBILITY_REASONS) {
						continue;
					}
					boolean asSource = rule.sourceCategory().equals(product.categorySlug())
							&& rule.targetCategory().equals(other.categorySlug());
					boolean asTarget = rule.targetCategory().equals(product.categorySlug())
							&& rule.sourceCategory().equals(other.categorySlug());
					if ((asSource && CompatibilityChecker.evaluatePair(rule, product, other).orElse(false))
							|| (asTarget && CompatibilityChecker.evaluatePair(rule, other, product).orElse(false))) {
						reasons.add("works with the " + other.product().name() + " (" + rule.label() + ")");
						compatibilityReasons++;
					}
				}
			}
			else if (rule.operator().spatial() && rule.sourceCategory().equals(product.categorySlug())
					&& CompatibilityChecker.evaluateSpace(rule, product, problem.space()).orElse(false)) {
				reasons.add(rule.label());
				compatibilityReasons++;
			}
			else if (rule.operator() == Operator.REQUIRES_CATEGORY_UNLESS
					&& rule.sourceCategory().equals(product.categorySlug())
					&& !CompatibilityChecker.selfSufficient(rule, product)) {
				reasons.add(rule.label());
				compatibilityReasons++;
			}
		}

		String sentence = String.join("; ", reasons);
		return Character.toUpperCase(sentence.charAt(0)) + sentence.substring(1) + ".";
	}

	static String summary(Problem problem, Tier tier, Solution solution, boolean contextRelaxed) {
		long required = solution.chosen().stream().filter(p -> p.need().required()).count();
		long optional = solution.chosen().size() - required;
		String extras = optional == 0 ? "" : ", plus " + optional + (optional == 1 ? " nice-to-have" : " nice-to-haves");
		String lead = switch (tier) {
			case BUDGET -> "The lowest-cost way to cover " + (required == 1 ? "your must-have" : "all " + required + " must-haves") + extras + ".";
			case BALANCED -> "The best value: quality where it matters" + extras + ".";
			case PREMIUM -> "Higher-end picks across the board" + extras + ".";
		};
		String money = " Total " + money(solution.costCents()) + ", leaving " + money(problem.budgetCents() - solution.costCents())
				+ " of your " + money(problem.budgetCents()) + " budget.";
		String relaxed = contextRelaxed && problem.context().contextTag() != null
				? " Includes general-purpose products because none made for " + humanize(problem.context().contextTag())
						+ " fit the budget."
				: "";
		return lead + money + relaxed;
	}

	/** Reminders about compatibility with equipment the customer already owns (the engine cannot verify it). */
	static List<String> ownedNotes(Problem problem, List<Solution> solutions) {
		Set<String> notes = new LinkedHashSet<>();
		Set<String> pickedCategories = new LinkedHashSet<>();
		solutions.forEach(s -> s.chosen().forEach(p -> pickedCategories.add(p.need().categorySlug())));
		for (OwnedCategory owned : problem.owned()) {
			for (Rule rule : problem.rules()) {
				if (!rule.involves(owned.slug()) || rule.operator().spatial() || rule.universal()) {
					continue;
				}
				String other = owned.slug().equals(rule.sourceCategory()) ? rule.targetCategory() : rule.sourceCategory();
				if (other != null && pickedCategories.contains(other)) {
					notes.add("You already own " + owned.name().toLowerCase(Locale.ROOT) + ": check that it works with the new "
							+ problem.categoryNames().getOrDefault(other, other).toLowerCase(Locale.ROOT) + " ("
							+ rule.label() + ").");
				}
			}
		}
		return List.copyOf(notes);
	}

	static String money(long cents) {
		return NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents, 2));
	}

	static String humanize(String tag) {
		return tag.replace('-', ' ');
	}
}
