package com.buildmybundle.recommendation.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.compatibility.CompatibilityChecker;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.compatibility.CompatibilityModels.Severity;
import com.buildmybundle.recommendation.engine.EngineModels.Need;
import com.buildmybundle.recommendation.engine.EngineModels.Pick;
import com.buildmybundle.recommendation.engine.EngineModels.Problem;
import com.buildmybundle.recommendation.engine.EngineModels.SearchResult;
import com.buildmybundle.recommendation.engine.EngineModels.Solution;

/**
 * Chooses one product per category to maximise a score subject to a budget and pairwise compatibility:
 * a multiple-choice knapsack with conflict constraints, solved exactly by depth-first branch and bound.
 *
 * <h2>Algorithm</h2>
 * <ol>
 * <li><b>Candidates</b> per category: active, enough stock for the quantity, fits the customer's space,
 * affordable on its own. Optional categories also get a "none" option.</li>
 * <li><b>Dominance pruning</b>: within a category, a product is dropped if another product with identical
 * values for every rule-relevant attribute is at most as expensive and scores at least as well. Such a
 * product can always be swapped in without breaking the budget or any rule, so no optimum is lost.</li>
 * <li><b>Cap</b>: at most {@value #MAX_OPTIONS_PER_CATEGORY} options per category, by score.</li>
 * <li><b>Search</b>: categories with the fewest options first; options tried best-score first so good
 * solutions are found early. A branch is cut when
 * <ul>
 * <li>{@code cost + Σ cheapest remaining options > budget} (cannot stay within budget), or</li>
 * <li>{@code score + Σ best remaining scores ≤ best found} (cannot beat the incumbent),</li>
 * </ul>
 * and every new choice is checked for compatibility against the choices already made. Presence rules
 * ("an XLR microphone needs an interface") are checked on complete bundles.</li>
 * </ol>
 *
 * <h2>Complexity</h2> Worst case {@code O(K^C)} time for C categories with K options each, and {@code O(C)}
 * stack. With C ≤ 20 and K ≤ 12 the bounds keep real searches to a few thousand nodes; a node and time limit
 * guarantee termination, in which case the best bundle found so far is returned and flagged as incomplete.
 * A dynamic programme over the budget ({@code O(C·K·B)}) would solve the unconstrained problem, but pairwise
 * compatibility breaks its optimal substructure, so branch and bound is used for the general case.
 */
public final class BundleOptimizer {

	static final int MAX_OPTIONS_PER_CATEGORY = 12;
	private static final double EPSILON = 1e-9;

	/** Search limits; the defaults are far above what the catalog needs. */
	public record Limits(long maxNodes, long timeBudgetNanos) {

		public static final Limits DEFAULT = new Limits(300_000, 2_000_000_000L);
	}

	@FunctionalInterface
	public interface OptionScorer {

		double score(Need need, ProductFacts product, long costCents);
	}

	private record Option(ProductFacts product, long cost, double score) {

		static final Option NONE = new Option(null, 0, 0);

		long id() {
			return product == null ? 0 : product.id();
		}
	}

	private record Var(int needIndex, Need need, List<Option> options) {
	}

	private BundleOptimizer() {
	}

	/** True when a product could be bought for a need at all (ignores budget and other products). */
	public static boolean eligible(ProductFacts product, Need need, Problem problem) {
		return product.product().active() && product.product().availableQuantity() >= need.quantity()
				&& CompatibilityChecker.fitsSpace(product, problem.space(), problem.rules());
	}

	public static SearchResult solve(Problem problem, List<Need> needs, OptionScorer scorer, long budgetCents,
			Predicate<ProductFacts> filter, Limits limits) {
		List<Var> vars = new ArrayList<>();
		for (int i = 0; i < needs.size(); i++) {
			Need need = needs.get(i);
			List<Option> options = new ArrayList<>();
			for (ProductFacts candidate : problem.candidates().getOrDefault(need.categorySlug(), List.of())) {
				if (!eligible(candidate, need, problem) || !filter.test(candidate)) {
					continue;
				}
				long cost = Scoring.cents(candidate) * need.quantity();
				if (cost <= budgetCents) {
					options.add(new Option(candidate, cost, scorer.score(need, candidate, cost)));
				}
			}
			options = prune(options, signatureAttributes(need.categorySlug(), problem.rules()));
			if (!need.required()) {
				options.add(Option.NONE);
			}
			if (options.isEmpty()) {
				return new SearchResult(null, 0, true); // a required category has nothing that can be bought
			}
			options.sort(Comparator.comparingDouble(Option::score).reversed()
				.thenComparingLong(Option::cost)
				.thenComparingLong(Option::id));
			vars.add(new Var(i, need, options));
		}
		vars.sort(Comparator.comparingInt((Var v) -> v.options().size()).thenComparingInt(Var::needIndex));
		return new Search(problem, needs, vars, budgetCents, limits).run();
	}

	/** Dominance pruning within groups of identical rule-relevant attributes, then a cap by score. */
	private static List<Option> prune(List<Option> options, Set<String> signatureAttributes) {
		Map<String, List<Option>> groups = options.stream()
			.collect(Collectors.groupingBy(o -> signature(o.product(), signatureAttributes), LinkedHashMap::new,
					Collectors.toList()));
		List<Option> kept = new ArrayList<>();
		for (List<Option> group : groups.values()) {
			group.sort(Comparator.comparingLong(Option::cost)
				.thenComparing(Comparator.comparingDouble(Option::score).reversed())
				.thenComparingLong(Option::id));
			double bestScore = Double.NEGATIVE_INFINITY;
			for (Option option : group) {
				if (option.score() > bestScore + EPSILON) {
					kept.add(option);
					bestScore = option.score();
				}
			}
		}
		kept.sort(Comparator.comparingDouble(Option::score).reversed().thenComparingLong(Option::cost));
		return new ArrayList<>(kept.subList(0, Math.min(kept.size(), MAX_OPTIONS_PER_CATEGORY)));
	}

	private static Set<String> signatureAttributes(String categorySlug, List<Rule> rules) {
		Set<String> attributes = new TreeSet<>();
		for (Rule rule : rules) {
			String attribute = rule.attributeFor(categorySlug);
			if (attribute != null) {
				attributes.add(attribute);
			}
		}
		return attributes;
	}

	private static String signature(ProductFacts product, Set<String> attributes) {
		return attributes.stream().map(a -> a + "=" + product.values(a)).collect(Collectors.joining(";"));
	}

	/** One depth-first search over the ordered variables. */
	private static final class Search {

		private final Problem problem;
		private final List<Need> needs;
		private final List<Var> vars;
		private final long budget;
		private final long maxNodes;
		private final long deadline;
		private final long[] minCostFrom;
		private final double[] maxScoreFrom;
		private final Option[] chosen;
		private final Map<Long, Boolean> pairCache = new HashMap<>();
		private long nodes;
		private boolean aborted;
		private Solution best;

		Search(Problem problem, List<Need> needs, List<Var> vars, long budget, Limits limits) {
			this.problem = problem;
			this.needs = needs;
			this.vars = vars;
			this.budget = budget;
			this.maxNodes = limits.maxNodes();
			this.deadline = System.nanoTime() + limits.timeBudgetNanos();
			int n = vars.size();
			this.minCostFrom = new long[n + 1];
			this.maxScoreFrom = new double[n + 1];
			for (int i = n - 1; i >= 0; i--) {
				List<Option> options = vars.get(i).options();
				minCostFrom[i] = minCostFrom[i + 1] + options.stream().mapToLong(Option::cost).min().orElse(0);
				maxScoreFrom[i] = maxScoreFrom[i + 1] + options.stream().mapToDouble(Option::score).max().orElse(0);
			}
			this.chosen = new Option[n];
		}

		SearchResult run() {
			if (minCostFrom[0] <= budget) {
				dfs(0, 0, 0);
			}
			return new SearchResult(best, nodes, !aborted);
		}

		private void dfs(int depth, long cost, double score) {
			nodes++;
			if (nodes > maxNodes || ((nodes & 1023) == 0 && System.nanoTime() > deadline)) {
				aborted = true;
				return;
			}
			if (depth == vars.size()) {
				leaf(cost, score);
				return;
			}
			for (Option option : vars.get(depth).options()) {
				double newScore = score + option.score();
				if (best != null && newScore + maxScoreFrom[depth + 1] <= best.score() + EPSILON) {
					break; // options are sorted by score: no later option can do better
				}
				long newCost = cost + option.cost();
				if (newCost + minCostFrom[depth + 1] > budget) {
					continue; // a cheaper option further down may still fit
				}
				if (option.product() != null && !compatibleWithChosen(option.product(), depth)) {
					continue;
				}
				chosen[depth] = option;
				dfs(depth + 1, newCost, newScore);
				chosen[depth] = null;
				if (aborted) {
					return;
				}
			}
		}

		private void leaf(long cost, double score) {
			List<ProductFacts> products = new ArrayList<>();
			for (Option option : chosen) {
				if (option.product() != null) {
					products.add(option.product());
				}
			}
			if (products.isEmpty()) {
				return; // a bundle must contain at least one product
			}
			boolean presenceOk = CompatibilityChecker.presenceViolations(products, problem.rules())
				.stream()
				.noneMatch(v -> v.severity() == Severity.BLOCK);
			if (!presenceOk || (best != null && score <= best.score() + EPSILON)) {
				return;
			}
			Pick[] picks = new Pick[needs.size()];
			for (int i = 0; i < vars.size(); i++) {
				Var var = vars.get(i);
				picks[var.needIndex()] = new Pick(var.need(), chosen[i].product());
			}
			best = new Solution(List.of(picks), cost, score);
		}

		private boolean compatibleWithChosen(ProductFacts product, int depth) {
			for (int i = 0; i < depth; i++) {
				ProductFacts other = chosen[i].product();
				if (other != null && !compatible(product, other)) {
					return false;
				}
			}
			return true;
		}

		private boolean compatible(ProductFacts a, ProductFacts b) {
			long key = Math.min(a.id(), b.id()) << 32 | Math.max(a.id(), b.id());
			return pairCache.computeIfAbsent(key, k -> CompatibilityChecker.pairCompatible(a, b, problem.rules()));
		}
	}
}
