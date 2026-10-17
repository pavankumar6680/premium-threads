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
import java.util.stream.Collectors;

import com.buildmybundle.goals.GoalModels.Quality;
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
 * The model is treated as an untrusted suggestion:
 * <ul>
 * <li>only templates, categories, usage tags and brands that exist in the store are accepted;</li>
 * <li>a budget is accepted only if the rules found none and the number actually appears in the text;</li>
 * <li>the rules' template, budget and non-default quality win when present (they are exact);</li>
 * <li>the model may add categories, mark categories as already owned and ask up to two clarifying
 * questions.</li>
 * </ul>
 * Anything that fails validation is dropped silently, so the worst case equals the rules-only result.
 */
public final class AiGoalMerger {

	public static final String SOURCE = "AI";
	static final int MAX_AI_QUESTIONS = 2;
	static final int MAX_ADDED_CATEGORIES = 10;
	private static final int MAX_QUESTION_LENGTH = 160;
	private static final BigDecimal MAX_BUDGET = new BigDecimal("1000000");
	private static final JsonMapper JSON = JsonMapper.builder().build();

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
		Map<String, TemplateTerms> templates = vocabulary.templates()
			.stream()
			.collect(Collectors.toMap(TemplateTerms::slug, t -> t));

		String templateSlug = rules.templateSlug() != null ? rules.templateSlug()
				: (ai.templateSlug() != null && templates.containsKey(ai.templateSlug()) ? ai.templateSlug() : null);
		TemplateTerms template = templateSlug == null ? null : templates.get(templateSlug);

		BigDecimal budget = rules.budget() != null ? rules.budget() : groundedBudget(ai.budget(), text);
		Quality quality = rules.quality() != Quality.BALANCED ? rules.quality() : parseQuality(ai.quality());

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
		int added = 0;
		for (AiCategory c : nullSafe(ai.categories())) {
			Optional<RequirementKind> kind = parseKind(c == null ? null : c.kind());
			if (c == null || c.slug() == null || !vocabulary.categoryPhrases().containsKey(c.slug()) || kind.isEmpty()) {
				continue;
			}
			Item existing = items.get(c.slug());
			if (existing == null) {
				if (added++ < MAX_ADDED_CATEGORIES) {
					items.put(c.slug(), new Item(c.slug(), kind.get(), quantity(c.quantity())));
				}
			}
			else if (kind.get() == RequirementKind.OWNED
					|| (kind.get() == RequirementKind.REQUIRED && existing.kind() == RequirementKind.OPTIONAL)) {
				items.put(c.slug(), new Item(c.slug(), kind.get(), existing.quantity()));
			}
		}

		Set<String> allowedTags = template != null ? new LinkedHashSet<>(template.usageTags()) : vocabulary.tags();
		Set<String> tags = new LinkedHashSet<>(rules.usageTags());
		nullSafe(ai.usageTags()).stream().filter(allowedTags::contains).forEach(tags::add);

		Map<String, String> brandsByLower = vocabulary.brands()
			.stream()
			.collect(Collectors.toMap(b -> b.toLowerCase(Locale.ROOT), b -> b, (a, b) -> a));
		Set<String> brands = new LinkedHashSet<>(rules.preferredBrands());
		nullSafe(ai.preferredBrands()).stream()
			.filter(b -> b != null && brandsByLower.containsKey(b.trim().toLowerCase(Locale.ROOT)))
			.map(b -> brandsByLower.get(b.trim().toLowerCase(Locale.ROOT)))
			.forEach(brands::add);

		List<Item> itemList = List.copyOf(items.values());
		List<String> questions = new ArrayList<>();
		if (budget == null) {
			questions.add("What is your total budget?");
		}
		if (itemList.stream().noneMatch(i -> i.kind() != RequirementKind.OWNED)) {
			questions.add("Which products do you need? For example: a desk, a chair and a monitor.");
		}
		nullSafe(ai.clarifyingQuestions()).stream()
			.filter(q -> q != null && !q.isBlank())
			.map(String::trim)
			.filter(q -> q.length() <= MAX_QUESTION_LENGTH && q.endsWith("?"))
			.filter(q -> !(budget == null && q.toLowerCase(Locale.ROOT).contains("budget")))
			.distinct()
			.limit(MAX_AI_QUESTIONS)
			.forEach(questions::add);

		return new GoalAnalysis(SOURCE, templateSlug,
				template != null ? template.title() : RuleBasedGoalInterpreter.CUSTOM_TITLE, budget, quality, itemList,
				List.copyOf(tags), List.copyOf(brands), rules.matchedTerms(), List.copyOf(new LinkedHashSet<>(questions)));
	}

	/** A model-proposed budget is only trusted if that number is literally in the customer's text. */
	static BigDecimal groundedBudget(BigDecimal proposed, String text) {
		if (proposed == null || proposed.signum() <= 0 || proposed.compareTo(MAX_BUDGET) > 0) {
			return null;
		}
		String normalized = text.toLowerCase(Locale.ROOT).replace(",", "");
		String whole = proposed.setScale(0, RoundingMode.DOWN).toPlainString();
		String thousands = proposed.divide(BigDecimal.valueOf(1000)).stripTrailingZeros().toPlainString();
		boolean present = normalized.matches("(?s).*(?<![\\d.])" + java.util.regex.Pattern.quote(whole) + "(?![\\d]).*")
				|| normalized.matches("(?s).*(?<![\\d.])" + java.util.regex.Pattern.quote(thousands) + "\\s*(k|thousand)\\b.*");
		return present ? proposed.setScale(2, RoundingMode.HALF_UP) : null;
	}

	private static Quality parseQuality(String value) {
		try {
			return value == null ? Quality.BALANCED : Quality.valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			return Quality.BALANCED;
		}
	}

	private static Optional<RequirementKind> parseKind(String value) {
		try {
			return Optional.of(value == null ? RequirementKind.REQUIRED : RequirementKind.valueOf(value.trim().toUpperCase(Locale.ROOT)));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private static int quantity(Integer value) {
		return value == null ? 1 : Math.max(1, Math.min(10, value));
	}

	private static <T> List<T> nullSafe(List<T> list) {
		return list == null ? List.of() : list;
	}
}
