package com.buildmybundle.recommendation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.buildmybundle.ai.LanguageModel;
import com.buildmybundle.ai.LanguageModel.AiStatus;
import com.buildmybundle.ai.LanguageModel.Format;
import com.buildmybundle.bundles.BundleModels.BundleView;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.recommendation.RecommendationViews.RunView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Writes a plain-language overview of a recommendation run with the local language model.
 *
 * <p>
 * The model only sees facts produced by the engine (bundles, totals, items, notes, or why nothing fits and
 * what to change) and is told to use nothing else. Its answer is rejected if it mentions any number that is
 * not in those facts, so it cannot invent prices or totals. Accepted overviews are cached with the run.
 */
@Service
public class RunExplainer {

	private static final Logger log = LoggerFactory.getLogger(RunExplainer.class);
	private static final String KIND = "AI_OVERVIEW";
	private static final int MAX_LENGTH = 1500;
	private static final int MIN_LENGTH = 40;

	static final String SYSTEM_PROMPT = """
			You are a friendly personal stylist for Premium Threads. Using ONLY the facts provided, write an overview
			in 3 to 5 sentences. If there are outfits, say why the pieces go together (colors, style, occasion) and
			which kind of shopper each outfit suits. If no outfit fits, explain why and the most practical way forward
			using the suggestions. Never mention a brand, product, price, size, color or number that is not in the
			facts. Plain text only: no lists, no headings, no markdown.""";

	public record Explanation(String status, String text, String model, boolean cached, String message) {

		static Explanation unavailable(String model, String message) {
			return new Explanation("UNAVAILABLE", null, model, false, message);
		}
	}

	private final RecommendationService recommendations;
	private final LanguageModel languageModel;
	private final ProductCatalog catalog;
	private final JdbcClient jdbc;

	public RunExplainer(RecommendationService recommendations, LanguageModel languageModel, ProductCatalog catalog,
			JdbcClient jdbc) {
		this.recommendations = recommendations;
		this.languageModel = languageModel;
		this.catalog = catalog;
		this.jdbc = jdbc;
	}

	public Explanation explain(UUID runId) {
		RunView run = recommendations.get(runId); // 404 for unknown runs
		AiStatus status = languageModel.status();
		Optional<String> cached = cached(runId);
		if (cached.isPresent()) {
			return new Explanation("OK", cached.get(), status.model(), true, null);
		}
		if (!status.available()) {
			return Explanation.unavailable(status.model(), status.message());
		}
		String facts = facts(run);
		Optional<String> answer = languageModel.complete(SYSTEM_PROMPT, facts, Format.TEXT).map(RunExplainer::clean);
		if (answer.isEmpty()) {
			return Explanation.unavailable(status.model(), "The AI model did not answer in time. Please try again.");
		}
		String text = answer.get();
		if (text.length() < MIN_LENGTH || text.length() > MAX_LENGTH || !Grounding.numbersGrounded(text, facts)
				|| !Grounding.brandsGrounded(text, facts, catalog.brands())) {
			log.info("Rejected AI overview for run {}: not grounded in the run's facts", runId);
			return new Explanation("REJECTED", null, status.model(), false,
					"The AI answer mentioned details that are not in your results, so it was discarded. Please try again.");
		}
		jdbc.sql("INSERT INTO recommendation_run_messages (run_id, kind, message, position) VALUES (:run, :kind, :msg, 0)")
			.param("run", runId)
			.param("kind", KIND)
			.param("msg", text.length() > 500 ? text.substring(0, 500) : text)
			.update();
		return new Explanation("OK", text, status.model(), false, null);
	}

	private Optional<String> cached(UUID runId) {
		return jdbc.sql("SELECT message FROM recommendation_run_messages WHERE run_id = :run AND kind = :kind LIMIT 1")
			.param("run", runId)
			.param("kind", KIND)
			.query(String.class)
			.optional();
	}

	/** The only information the model is given. */
	static String facts(RunView run) {
		StringBuilder sb = new StringBuilder();
		sb.append("Goal: ").append(run.goalTitle()).append(". Budget: ").append(money(run.budget())).append(".\n");
		if (run.bundles().isEmpty()) {
			sb.append("No bundle fits this goal.\n");
			run.reasons().forEach(r -> sb.append("Reason: ").append(r).append('\n'));
			if (run.minimumFeasibleCost() != null) {
				sb.append("Cheapest compatible setup: ").append(money(run.minimumFeasibleCost())).append(".\n");
			}
			run.suggestions().forEach(s -> sb.append("Suggestion: ").append(s).append('\n'));
			return sb.toString();
		}
		sb.append("The shopper prefers the ").append(run.preferredTier().toLowerCase(Locale.ROOT)).append(" approach.\n");
		for (BundleView b : run.bundles()) {
			int share = b.currentTotal().multiply(BigDecimal.valueOf(100))
				.divide(b.budget(), 0, RoundingMode.HALF_UP).intValue();
			sb.append(b.name()).append(": total ").append(money(b.currentTotal())).append(", ").append(share)
				.append("% of the budget, ").append(money(b.budget().subtract(b.currentTotal()))).append(" left. ");
			sb.append(b.items().stream()
				.map(i -> i.categoryName() + ": " + i.name() + " by " + i.brand() + " (" + money(i.lineTotal()) + variant(i)
						+ ", rated " + i.ratingAvg().stripTrailingZeros().toPlainString() + "/5"
						+ (i.optional() ? ", nice to have" : "") + ")")
				.collect(Collectors.joining("; ")));
			sb.append(". ").append(b.compatibility().compatible() ? "Every piece works with the others." : "Has conflicts between pieces.");
			if (b.summary() != null && b.summary().contains("Colors:")) {
				sb.append(' ').append(b.summary().substring(b.summary().indexOf("Colors:")));
			}
			sb.append('\n');
		}
		run.notes().forEach(n -> sb.append("Note: ").append(n).append('\n'));
		return sb.toString();
	}

	private static String variant(com.buildmybundle.bundles.BundleModels.ItemView item) {
		if (item.variant() == null) {
			return item.needsSize() ? ", size not chosen yet" : "";
		}
		String size = item.variant().size().equalsIgnoreCase("One size") ? "" : ", size " + item.variant().size();
		String color = "Standard".equals(item.variant().color()) ? "" : ", " + item.variant().color();
		return size + color;
	}

	static String clean(String text) {
		return text.replaceAll("[*#`_]+", "").replaceAll("\\s+", " ").trim();
	}

	private static String money(BigDecimal amount) {
		return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
	}
}
