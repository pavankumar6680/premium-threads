package com.buildmybundle.recommendation.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.compatibility.CompatibilityChecker;
import com.buildmybundle.compatibility.CompatibilityModels.Operator;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.recommendation.engine.BundleOptimizer.Limits;
import com.buildmybundle.recommendation.engine.BundleOptimizer.OptionScorer;
import com.buildmybundle.recommendation.engine.EngineModels.EngineResult;
import com.buildmybundle.recommendation.engine.EngineModels.Infeasibility;
import com.buildmybundle.recommendation.engine.EngineModels.Need;
import com.buildmybundle.recommendation.engine.EngineModels.Problem;
import com.buildmybundle.recommendation.engine.EngineModels.SearchResult;
import com.buildmybundle.recommendation.engine.EngineModels.Solution;
import com.buildmybundle.recommendation.engine.EngineModels.Tier;
import com.buildmybundle.recommendation.engine.EngineModels.TierBundle;

/**
 * Produces up to three meaningfully different bundles (Budget, Balanced, Premium) for a goal, or explains
 * why none is possible. Pure and deterministic: the same inputs always give the same bundles.
 *
 * <ul>
 * <li>Products made for the goal's purpose (its context tag, e.g. "gaming") are preferred: in categories
 * where such products exist, only they are considered. If that makes a tier impossible, the tier is
 * re-solved with all products and the bundle says so.</li>
 * <li>Identical tiers are not repeated; a note explains it instead of padding the result.</li>
 * <li>When nothing fits, the engine reports the cheapest compatible cost of the must-haves and which
 * single must-have to drop (or mark as owned) to fit the budget, or the conflict that makes it impossible.</li>
 * </ul>
 */
public final class RecommendationEngine {

	public static final String ALGORITHM_VERSION = "bnb-1.0";
	private static final int MAX_DROP_SUGGESTIONS = 3;

	private RecommendationEngine() {
	}

	public static EngineResult recommend(Problem problem, Limits limits) {
		long started = System.nanoTime();
		List<Need> required = problem.needs().stream().filter(Need::required).toList();
		Predicate<ProductFacts> contextFilter = contextFilter(problem);

		List<TierBundle> bundles = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		Map<String, Tier> seen = new LinkedHashMap<>();
		long nodes = 0;
		boolean complete = true;

		for (Tier tier : Tier.values()) {
			List<Need> needs = tier == Tier.BUDGET && !required.isEmpty() ? required : problem.needs();
			OptionScorer scorer = (need, product, cost) -> Scoring.optionScore(need, product, cost, tier,
					problem.context(), problem.budgetCents());
			SearchResult result = BundleOptimizer.solve(problem, needs, scorer, problem.budgetCents(), contextFilter, limits);
			boolean relaxed = false;
			nodes += result.nodes();
			complete &= result.complete();
			if (result.best() == null && problem.context().contextTag() != null) {
				result = BundleOptimizer.solve(problem, needs, scorer, problem.budgetCents(), p -> true, limits);
				relaxed = result.best() != null;
				nodes += result.nodes();
				complete &= result.complete();
			}
			Solution best = result.best();
			if (best == null) {
				continue;
			}
			Tier duplicateOf = seen.get(best.contentKey());
			if (duplicateOf != null) {
				notes.add("The " + tier.displayName.toLowerCase() + " would be identical to the "
						+ duplicateOf.displayName.toLowerCase()
						+ ", so it is not shown separately: your budget and the catalog do not allow a meaningfully different option.");
				continue;
			}
			seen.put(best.contentKey(), tier);
			bundles.add(new TierBundle(tier, best, Explanations.forSolution(problem, tier, best),
					Explanations.summary(problem, tier, best, relaxed), relaxed));
		}

		Infeasibility infeasibility = null;
		if (bundles.isEmpty()) {
			infeasibility = diagnose(problem, limits);
		}
		else {
			notes.addAll(Explanations.ownedNotes(problem, bundles.stream().map(TierBundle::solution).toList()));
		}
		if (!complete) {
			notes.add("The search stopped early to stay fast, so these are the best bundles found rather than proven optimal.");
		}
		return new EngineResult(bundles, notes, infeasibility, nodes, complete, (System.nanoTime() - started) / 1_000_000);
	}

	/** Result of re-optimizing one bundle: either a bundle or the reason none fits. */
	public record TierOutcome(TierBundle bundle, Infeasibility infeasibility, long nodes) {
	}

	/**
	 * Re-optimizes a single tier while keeping {@code locked} products (category slug → product id) fixed:
	 * the workspace's "re-optimize around my locked items". Every need is filled; the purpose filter applies
	 * to unlocked categories and is relaxed if nothing fits.
	 */
	public static TierOutcome optimizeTier(Problem problem, Tier tier, Map<String, Long> locked, Limits limits) {
		Predicate<ProductFacts> lockFilter = p -> !locked.containsKey(p.categorySlug())
				|| locked.get(p.categorySlug()).equals(p.id());
		Predicate<ProductFacts> context = contextFilter(problem);
		Predicate<ProductFacts> purpose = p -> locked.containsKey(p.categorySlug()) || context.test(p);
		OptionScorer scorer = (need, product, cost) -> Scoring.optionScore(need, product, cost, tier,
				problem.context(), problem.budgetCents());

		SearchResult result = BundleOptimizer.solve(problem, problem.needs(), scorer, problem.budgetCents(),
				lockFilter.and(purpose), limits);
		long nodes = result.nodes();
		boolean relaxed = false;
		if (result.best() == null && problem.context().contextTag() != null) {
			result = BundleOptimizer.solve(problem, problem.needs(), scorer, problem.budgetCents(), lockFilter, limits);
			nodes += result.nodes();
			relaxed = result.best() != null;
		}
		if (result.best() != null) {
			Solution best = result.best();
			return new TierOutcome(new TierBundle(tier, best, Explanations.forSolution(problem, tier, best),
					Explanations.summary(problem, tier, best, relaxed), relaxed), null, nodes);
		}

		SearchResult cheapest = BundleOptimizer.solve(problem, problem.needs(), (need, product, cost) -> -cost,
				Long.MAX_VALUE, lockFilter, limits);
		nodes += cheapest.nodes();
		if (cheapest.best() != null) {
			long minCost = cheapest.best().costCents();
			return new TierOutcome(null, new Infeasibility(
					List.of("Your locked items plus the cheapest compatible choices for everything else cost "
							+ Explanations.money(minCost) + ", which is more than your "
							+ Explanations.money(problem.budgetCents()) + " budget."),
					minCost,
					List.of("Raise the budget to at least " + Explanations.money(minCost) + ".",
							"Unlock an item or remove an optional one.")),
					nodes);
		}
		return new TierOutcome(null, new Infeasibility(
				List.of("No compatible combination of in-stock products works with your locked items."), null,
				List.of("Unlock an item or swap it for a compatible one.")), nodes);
	}

	/** In categories where some products are made for the goal's purpose, keep only those. */
	static Predicate<ProductFacts> contextFilter(Problem problem) {
		String tag = problem.context().contextTag();
		if (tag == null) {
			return p -> true;
		}
		Set<String> categoriesWithTag = new HashSet<>();
		problem.candidates().forEach((slug, products) -> {
			if (products.stream().anyMatch(p -> p.tags().contains(tag))) {
				categoriesWithTag.add(slug);
			}
		});
		return p -> !categoriesWithTag.contains(p.categorySlug()) || p.tags().contains(tag);
	}

	private static Infeasibility diagnose(Problem problem, Limits limits) {
		List<Need> mustHaves = problem.needs().stream().filter(Need::required).toList();
		if (mustHaves.isEmpty()) {
			mustHaves = problem.needs();
		}
		List<String> reasons = new ArrayList<>();
		List<String> suggestions = new ArrayList<>();

		// 1. A must-have with nothing that can be bought at all
		for (Need need : mustHaves) {
			List<ProductFacts> candidates = problem.candidates().getOrDefault(need.categorySlug(), List.of());
			if (candidates.stream().anyMatch(p -> BundleOptimizer.eligible(p, need, problem))) {
				continue;
			}
			boolean inStock = candidates.stream()
				.anyMatch(p -> p.product().active() && p.product().availableQuantity() >= need.quantity());
			reasons.add(inStock ? "No " + need.categoryName() + " fit your available space."
					: "No " + need.categoryName() + " are in stock" + (need.quantity() > 1 ? " in the quantity you need." : " right now."));
			suggestions.add("Mark " + need.categoryName() + " as already owned or remove it from your goal"
					+ (inStock ? ", or enter a larger space." : "."));
		}
		if (!reasons.isEmpty()) {
			return new Infeasibility(reasons, null, suggestions);
		}

		// 2. Cheapest compatible combination, ignoring the budget
		OptionScorer cheapest = (need, product, cost) -> -cost;
		SearchResult minimum = BundleOptimizer.solve(problem, mustHaves, cheapest, Long.MAX_VALUE, p -> true, limits);
		if (minimum.best() != null) {
			long minCost = minimum.best().costCents();
			reasons.add("The cheapest compatible combination of your must-haves costs " + Explanations.money(minCost)
					+ ", which is more than your " + Explanations.money(problem.budgetCents()) + " budget.");
			suggestions.add("Raise your budget to at least " + Explanations.money(minCost) + ".");
			if (mustHaves.size() > 1) {
				List<Map.Entry<Need, Long>> drops = new ArrayList<>();
				for (Need drop : mustHaves) {
					List<Need> rest = mustHaves.stream().filter(n -> n != drop).toList();
					SearchResult without = BundleOptimizer.solve(problem, rest, cheapest, problem.budgetCents(), p -> true, limits);
					if (without.best() != null) {
						drops.add(Map.entry(drop, without.best().costCents()));
					}
				}
				drops.stream()
					.sorted(Comparator.comparingLong(Map.Entry::getValue))
					.limit(MAX_DROP_SUGGESTIONS)
					.forEach(e -> suggestions.add("Mark " + e.getKey().categoryName()
							+ " as already owned or remove it: the remaining must-haves start at "
							+ Explanations.money(e.getValue()) + "."));
			}
			return new Infeasibility(reasons, minCost, suggestions);
		}

		// 3. No compatible combination exists at any price: find the conflicting pair or missing category
		for (int i = 0; i < mustHaves.size(); i++) {
			for (int j = i + 1; j < mustHaves.size(); j++) {
				conflict(problem, mustHaves.get(i), mustHaves.get(j)).ifPresent(reasons::add);
			}
		}
		for (Rule rule : problem.rules()) {
			if (rule.operator() != Operator.REQUIRES_CATEGORY_UNLESS) {
				continue;
			}
			boolean targetInGoal = problem.needs().stream().anyMatch(n -> n.categorySlug().equals(rule.targetCategory()));
			mustHaves.stream()
				.filter(n -> n.categorySlug().equals(rule.sourceCategory()))
				.filter(n -> problem.candidates().getOrDefault(n.categorySlug(), List.of()).stream()
					.filter(p -> BundleOptimizer.eligible(p, n, problem))
					.noneMatch(p -> CompatibilityChecker.selfSufficient(rule, p)))
				.findFirst()
				.filter(n -> !targetInGoal)
				.ifPresent(n -> {
					String target = problem.categoryNames().getOrDefault(rule.targetCategory(), rule.targetCategory());
					reasons.add(rule.message());
					suggestions.add("Add " + target + " to your goal.");
				});
		}
		if (reasons.isEmpty()) {
			reasons.add("No combination of the available products satisfies every compatibility rule.");
		}
		suggestions.add("Adjust your goal: remove a conflicting item or mark it as already owned.");
		return new Infeasibility(reasons, null, suggestions);
	}

	private static java.util.Optional<String> conflict(Problem problem, Need a, Need b) {
		List<Rule> rules = problem.rules().stream()
			.filter(r -> r.operator().pairwise())
			.filter(r -> (r.involves(a.categorySlug()) && r.involves(b.categorySlug())))
			.toList();
		if (rules.isEmpty()) {
			return java.util.Optional.empty();
		}
		List<ProductFacts> as = eligible(problem, a);
		List<ProductFacts> bs = eligible(problem, b);
		for (ProductFacts x : as) {
			for (ProductFacts y : bs) {
				if (CompatibilityChecker.pairCompatible(x, y, rules)) {
					return java.util.Optional.empty();
				}
			}
		}
		return java.util.Optional.of("None of the available " + a.categoryName() + " work with any available "
				+ b.categoryName() + ": " + rules.getFirst().message());
	}

	private static List<ProductFacts> eligible(Problem problem, Need need) {
		return problem.candidates()
			.getOrDefault(need.categorySlug(), List.of())
			.stream()
			.filter(p -> BundleOptimizer.eligible(p, need, problem))
			.toList();
	}
}
