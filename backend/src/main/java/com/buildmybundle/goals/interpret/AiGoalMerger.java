package com.buildmybundle.goals.interpret;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.buildmybundle.goals.GoalModels.RequirementKind;
import com.buildmybundle.goals.interpret.GoalAnalysis.Item;
import com.buildmybundle.goals.interpret.InterpreterVocabulary.TemplateItem;
import com.buildmybundle.goals.interpret.InterpreterVocabulary.TemplateTerms;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.json.JsonMapper;

/**
 * Validates a language model's reading of a goal and merges it with the rule-based reading.
 *
 * <p>
 * The model is an untrusted suggestion, and a valid store value is not enough: testing a small local model
 * showed it adding real categories and brands the shopper never asked for. So every contribution must be
 * <em>grounded in the shopper's text</em> or fill a gap the rules left:
 * <ul>
 * <li><b>template</b>: only when the rules found none (e.g. "competitive shooters" → gaming setup);</li>
 * <li><b>budget</b>: only when the rules found none and the number literally appears in the text;</li>
 * <li><b>owned items</b>: the model may mark a category as already owned only if the text names it in a
 * sentence that is not a request ("I need a desk, a chair…" is never read as owning them);</li>
 * <li><b>usage tags</b>: only those offered by the chosen template;</li>
 * <li><b>questions</b>: at most one, and only when something essential (budget or items) is missing.</li>
 * </ul>
 * The model cannot add categories, brands or a quality preference. Anything else is dropped silently, so the
 * worst case equals the rules-only result.
 */
public final class AiGoalMerger {

	public static final String SOURCE = "AI";
	static final int MAX_AI_QUESTIONS = 1;
	private static final int MAX_QUESTION_LENGTH = 160;
	private static final BigDecimal MAX_BUDGET = new BigDecimal("1000000");
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Pattern SENTENCE_BREAK = Pattern.compile("[.;!?]+|\\bbut\\b");
	private static final Pattern REQUEST = Pattern
		.compile("\\b(?:need|needs|want|wants|looking for|buy|buying|get me|require|requires|would like|shopping for)\\b");

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AiGoal(String templateSlug, BigDecimal budget, String quality, List<AiCategory> categories,
			List<String> usageTags, List<String> preferredBrands, List<String> clarifyingQuestions) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AiCategory(String slug, String kind, Integer quantity) {
	}

	private AiGoalMerger() {
	}

	/** Parses the model's answer, tolerating code fences or prose around the JSON object. */
	public static Optional<AiGoal> parse(String answer) {
		if (answer == null) {
			return Optional.empty();
		}
		int start = answer.indexOf('{');
		int end = answer.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return Optional.empty();
		}
		try {
			return Optional.of(JSON.readValue(answer.substring(start, end + 1), AiGoal.class));
		}
		catch (RuntimeException ex) {
			return Optional.empty();
		}
	}

	public static GoalAnalysis merge(GoalAnalysis rules, AiGoal ai, String text, InterpreterVocabulary vocabulary) {
		String normalized = RuleBasedGoalInterpreter.normalize(text);
		Map<String, TemplateTerms> templates = vocabulary.templates()
			.stream()
			.collect(Collectors.toMap(TemplateTerms::slug, t -> t));

		String templateSlug = rules.templateSlug() != null ? rules.templateSlug()
				: (ai.templateSlug() != null && templates.containsKey(ai.templateSlug()) ? ai.templateSlug() : null);
		TemplateTerms template = templateSlug == null ? null : templates.get(templateSlug);
		BigDecimal budget = rules.budget() != null ? rules.budget() : groundedBudget(ai.budget(), text);

		Map<String, Item> items = new LinkedHashMap<>();
		if (rules.templateSlug() == null && template != null) {
			for (TemplateItem ti : template.items()) {
				if (ti.required() || ti.selectedByDefault()) {
					items.put(ti.categorySlug(), new Item(ti.categorySlug(),
							ti.required() ? RequirementKind.REQUIRED : RequirementKind.OPTIONAL, ti.quantity()));
				}
			}
		}
		rules.items().forEach(i -> items.put(i.categorySlug(), i));
		for (AiCategory c : nullSafe(ai.categories())) {
			if (c != null && c.slug() != null && "OWNED".equalsIgnoreCase(String.valueOf(c.kind()).trim())
					&& namedOutsideARequest(c.slug(), normalized, vocabulary)) {
				items.put(c.slug(), new Item(c.slug(), RequirementKind.OWNED, 1));
			}
		}

		Set<String> tags = new LinkedHashSet<>(rules.usageTags());
		if (template != null) {
			nullSafe(ai.usageTags()).stream().filter(template.usageTags()::contains).forEach(tags::add);
		}

		List<Item> itemList = List.copyOf(items.values());
		List<String> questions = new ArrayList<>();
		if (budget == null) {
			questions.add("What is your total budget?");
		}
		if (itemList.stream().noneMatch(i -> i.kind() != RequirementKind.OWNED)) {
			questions.add("Which products do you need? For example: a desk, a chair and a monitor.");
		}
		if (!questions.isEmpty()) {
			nullSafe(ai.clarifyingQuestions()).stream()
				.filter(q -> q != null && !q.isBlank())
				.map(String::trim)
				.filter(q -> q.length() <= MAX_QUESTION_LENGTH && q.endsWith("?"))
				.filter(q -> !q.toLowerCase(Locale.ROOT).contains("budget"))
				.limit(MAX_AI_QUESTIONS)
				.forEach(questions::add);
		}

		return new GoalAnalysis(SOURCE, templateSlug,
				template != null ? template.title() : RuleBasedGoalInterpreter.CUSTOM_TITLE, budget, rules.quality(),
				itemList, List.copyOf(tags), rules.preferredBrands(), rules.matchedTerms(),
				List.copyOf(new LinkedHashSet<>(questions)));
	}

	/** True when a sentence of the text names the category (or its plural) without asking for it. */
	static boolean namedOutsideARequest(String slug, String normalizedText, InterpreterVocabulary vocabulary) {
		List<Pattern> phrases = vocabulary.categoryPhrases()
			.getOrDefault(slug, List.of())
			.stream()
			.map(p -> Pattern.compile("\\b" + Pattern.quote(RuleBasedGoalInterpreter.normalize(p)) + "(?:e?s)?\\b"))
			.toList();
		return SENTENCE_BREAK.splitAsStream(normalizedText)
			.filter(sentence -> !REQUEST.matcher(sentence).find())
			.anyMatch(sentence -> phrases.stream().anyMatch(p -> p.matcher(sentence).find()));
	}

	/** A model-proposed budget is only trusted if that number is literally in the customer's text. */
	static BigDecimal groundedBudget(BigDecimal proposed, String text) {
		if (proposed == null || proposed.signum() <= 0 || proposed.compareTo(MAX_BUDGET) > 0) {
			return null;
		}
		String normalized = text.toLowerCase(Locale.ROOT).replace(",", "");
		String whole = proposed.setScale(0, RoundingMode.DOWN).toPlainString();
		String thousands = proposed.divide(BigDecimal.valueOf(1000)).stripTrailingZeros().toPlainString();
		boolean present = normalized.matches("(?s).*(?<![\\d.])" + Pattern.quote(whole) + "(?![\\d]).*")
				|| normalized.matches("(?s).*(?<![\\d.])" + Pattern.quote(thousands) + "\\s*(k|thousand)\\b.*");
		return present ? proposed.setScale(2, RoundingMode.HALF_UP) : null;
	}

	private static <T> List<T> nullSafe(List<T> list) {
		return list == null ? List.of() : list;
	}
}
