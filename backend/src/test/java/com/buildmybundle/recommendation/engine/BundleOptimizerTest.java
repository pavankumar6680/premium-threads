package com.buildmybundle.recommendation.engine;

import static com.buildmybundle.recommendation.engine.TestProducts.product;
import static com.buildmybundle.recommendation.engine.TestProducts.requires;
import static com.buildmybundle.recommendation.engine.TestProducts.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.compatibility.CompatibilityChecker;
import com.buildmybundle.compatibility.CompatibilityModels.Operator;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.compatibility.CompatibilityModels.Severity;
import com.buildmybundle.compatibility.CompatibilityModels.Space;
import com.buildmybundle.recommendation.engine.BundleOptimizer.Limits;
import com.buildmybundle.recommendation.engine.BundleOptimizer.OptionScorer;
import com.buildmybundle.recommendation.engine.EngineModels.Need;
import com.buildmybundle.recommendation.engine.EngineModels.Pick;
import com.buildmybundle.recommendation.engine.EngineModels.Problem;
import com.buildmybundle.recommendation.engine.EngineModels.ScoringContext;
import com.buildmybundle.recommendation.engine.EngineModels.SearchResult;
import com.buildmybundle.recommendation.engine.EngineModels.Tier;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class BundleOptimizerTest {

	private static final List<Rule> RULES = List.of(
			rule("A_B_MATCH", "a", "b", Operator.EQUALS, "kind", "kind"),
			rule("C_FITS_D", "c", "d", Operator.LTE, "weight", "capacity"),
			rule("B_E_PORTS", "b", "e", Operator.LIST_INTERSECTS, "ports", "ports"),
			requires("A_NEEDS_E", "a", "e", "conn", "USB"));

	private static final ScoringContext CONTEXT = new ScoringContext(null, List.of(), List.of());

	/**
	 * Property test: on random small problems, branch and bound must reach exactly the optimal score found by
	 * exhaustive enumeration, never exceed the budget and only return compatible bundles.
	 */
	@RepeatedTest(400)
	void matchesExhaustiveSearchOnRandomProblems(org.junit.jupiter.api.RepetitionInfo repetition) {
		Random random = new Random(repetition.getCurrentRepetition());
		Problem problem = randomProblem(random);
		OptionScorer scorer = (need, p, cost) -> Scoring.optionScore(need, p, cost, Tier.BALANCED, CONTEXT,
				problem.budgetCents());

		SearchResult result = BundleOptimizer.solve(problem, problem.needs(), scorer, problem.budgetCents(), p -> true,
				Limits.DEFAULT);
		Double optimum = bruteForce(problem, scorer);

		if (optimum == null) {
			assertThat(result.best()).as("no feasible bundle exists").isNull();
			return;
		}
		assertThat(result.best()).as("a feasible bundle exists").isNotNull();
		assertThat(result.best().score()).isCloseTo(optimum, within(1e-9));
		assertThat(result.best().costCents()).isLessThanOrEqualTo(problem.budgetCents());
		assertThat(valid(problem, result.best().chosen())).isTrue();
		assertThat(result.complete()).isTrue();
	}

	@Test
	void choosesTheCheapestCompatiblePairWhenMinimisingCost() {
		ProductFacts camFx = product(1, "a", "500").attr("kind", "FX").attr("conn", "USB").build();
		ProductFacts camOm = product(2, "a", "400").attr("kind", "OM").attr("conn", "USB").build();
		ProductFacts lensFx = product(3, "b", "100").attr("kind", "FX").build();
		ProductFacts lensOmPricey = product(4, "b", "350").attr("kind", "OM").build();
		Problem problem = problem(List.of(need("a", true), need("b", true)), List.of(camFx, camOm, lensFx, lensOmPricey), 100_000);

		SearchResult result = BundleOptimizer.solve(problem, problem.needs(), (n, p, c) -> -c, Long.MAX_VALUE, p -> true,
				Limits.DEFAULT);

		// OM camera is cheaper, but FX camera + FX lens (600) beats OM camera + OM lens (750)
		assertThat(result.best().chosen()).extracting(p -> p.product().id()).containsExactlyInAnyOrder(1L, 3L);
		assertThat(result.best().costCents()).isEqualTo(60_000);
	}

	@Test
	void respectsStockAndQuantity() {
		ProductFacts lowStock = product(1, "c", "10").stock(1).build();
		ProductFacts enough = product(2, "c", "50").stock(5).build();
		Need twoUnits = new Need(1L, "c", "C", true, 2);
		Problem problem = problem(List.of(twoUnits), List.of(lowStock, enough), 100_000);

		SearchResult result = BundleOptimizer.solve(problem, problem.needs(), (n, p, c) -> -c, problem.budgetCents(),
				p -> true, Limits.DEFAULT);

		assertThat(result.best().chosen()).extracting(p -> p.product().id()).containsExactly(2L);
		assertThat(result.best().costCents()).isEqualTo(10_000);
	}

	@Test
	void stopsAtTheNodeLimitAndReportsAnIncompleteSearch() {
		List<ProductFacts> products = new ArrayList<>();
		List<Need> needs = new ArrayList<>();
		for (int c = 0; c < 8; c++) {
			needs.add(need("cat" + c, true));
			for (int i = 0; i < 6; i++) {
				products.add(product(c * 10 + i + 1, "cat" + c, String.valueOf(10 + i)).rating(3 + i * 0.3, 10 + i).build());
			}
		}
		Problem problem = problem(needs, products, 1_000_000);

		SearchResult result = BundleOptimizer.solve(problem, needs, (n, p, c) -> Scoring.quality(p) - c / 1e6,
				problem.budgetCents(), p -> true, new Limits(5, Long.MAX_VALUE));

		// a complete bundle needs 9 nodes (root + 8 categories), so a 5-node limit must stop the search
		assertThat(result.complete()).isFalse();
		assertThat(result.nodes()).isLessThanOrEqualTo(6);
	}

	// ------------------------------------------------------------------ helpers

	private static Need need(String category, boolean required) {
		return new Need((long) category.hashCode(), category, category.toUpperCase(), required, 1);
	}

	private static Problem problem(List<Need> needs, List<ProductFacts> products, long budgetCents) {
		Map<String, List<ProductFacts>> candidates = new LinkedHashMap<>();
		products.forEach(p -> candidates.computeIfAbsent(p.categorySlug(), k -> new ArrayList<>()).add(p));
		return new Problem(needs, candidates, RULES, budgetCents, Space.UNKNOWN, List.of(), CONTEXT, Map.of());
	}

	private static Problem randomProblem(Random random) {
		List<String> categories = new ArrayList<>(List.of("a", "b", "c", "d", "e"));
		int needCount = 2 + random.nextInt(4);
		List<Need> needs = new ArrayList<>();
		List<ProductFacts> products = new ArrayList<>();
		long id = 1;
		for (int i = 0; i < needCount; i++) {
			String category = categories.get(i);
			needs.add(need(category, random.nextDouble() < 0.7));
			int count = 1 + random.nextInt(5);
			for (int j = 0; j < count; j++) {
				TestProducts.Builder b = product(id++, category, String.valueOf(20 + random.nextInt(400)))
					.rating(3 + random.nextDouble() * 2, random.nextInt(2000))
					.tier(1 + random.nextInt(3))
					.stock(random.nextInt(4));
				b.attr("kind", random.nextBoolean() ? "FX" : "OM");
				b.attr("weight", String.valueOf(1 + random.nextInt(10)));
				b.attr("capacity", String.valueOf(1 + random.nextInt(10)));
				b.attr("ports", random.nextBoolean() ? "HDMI" : "DP");
				b.attr("conn", random.nextBoolean() ? "USB" : "XLR");
				products.add(b.build());
			}
		}
		return problem(needs, products, 5_000 + random.nextInt(120_000));
	}

	/** Exhaustive enumeration of every combination; returns the best score or null if nothing is feasible. */
	private static Double bruteForce(Problem problem, OptionScorer scorer) {
		List<List<ProductFacts>> domains = new ArrayList<>();
		for (Need need : problem.needs()) {
			List<ProductFacts> domain = new ArrayList<>();
			for (ProductFacts p : problem.candidates().getOrDefault(need.categorySlug(), List.of())) {
				if (BundleOptimizer.eligible(p, need, problem)) {
					domain.add(p);
				}
			}
			if (!need.required()) {
				domain.add(null);
			}
			domains.add(domain);
		}
		Double[] best = { null };
		enumerate(problem, scorer, domains, 0, new ArrayList<>(), best);
		return best[0];
	}

	private static void enumerate(Problem problem, OptionScorer scorer, List<List<ProductFacts>> domains, int index,
			List<Pick> picks, Double[] best) {
		if (index == domains.size()) {
			List<Pick> chosen = picks.stream().filter(p -> p.product() != null).toList();
			long cost = chosen.stream().mapToLong(Pick::costCents).sum();
			if (chosen.isEmpty() || cost > problem.budgetCents() || !valid(problem, chosen)) {
				return;
			}
			double score = chosen.stream().mapToDouble(p -> scorer.score(p.need(), p.product(), p.costCents())).sum();
			if (best[0] == null || score > best[0]) {
				best[0] = score;
			}
			return;
		}
		for (ProductFacts p : domains.get(index)) {
			picks.add(new Pick(problem.needs().get(index), p));
			enumerate(problem, scorer, domains, index + 1, picks, best);
			picks.removeLast();
		}
	}

	private static boolean valid(Problem problem, List<Pick> chosen) {
		List<ProductFacts> products = chosen.stream().map(Pick::product).toList();
		for (int i = 0; i < products.size(); i++) {
			for (int j = i + 1; j < products.size(); j++) {
				if (!CompatibilityChecker.pairCompatible(products.get(i), products.get(j), problem.rules())) {
					return false;
				}
			}
		}
		return CompatibilityChecker.presenceViolations(products, problem.rules())
			.stream()
			.noneMatch(v -> v.severity() == Severity.BLOCK);
	}
}
