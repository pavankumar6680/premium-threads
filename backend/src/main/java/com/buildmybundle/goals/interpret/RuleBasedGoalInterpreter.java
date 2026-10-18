package com.buildmybundle.goals.interpret;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.buildmybundle.goals.GoalModels.Fit;
import com.buildmybundle.goals.GoalModels.Quality;
import com.buildmybundle.goals.GoalModels.RequirementKind;
import com.buildmybundle.goals.interpret.GoalAnalysis.Item;
import com.buildmybundle.goals.interpret.InterpreterVocabulary.TemplateItem;
import com.buildmybundle.goals.interpret.InterpreterVocabulary.TemplateTerms;

/**
 * Deterministic goal interpreter: keyword and pattern rules over the store's own vocabulary. It works
 * without any AI service and never invents products or prices; it only maps words to templates,
 * categories, a budget and preferences, which the customer then reviews in the wizard.
 *
 * <p>
 * Rules, in order:
 * <ol>
 * <li>Template: the one whose keywords occur most often ("work from home" → home office).</li>
 * <li>Budget: "$1,200", "$1.5k", "budget of 900", "under 2k", "800 dollars". A bare "4k" is ignored so a
 * "4k monitor" is not mistaken for a budget.</li>
 * <li>Categories: phrases such as "desk", "ergonomic chair", "mouse". Ambiguous words ("lighting") are
 * resolved in favour of the matched template's categories.</li>
 * <li>Owned items: categories named in a clause like "I already have a monitor and keyboard".</li>
 * <li>Quality, usage tags and brands from fixed phrase lists and the catalog's brand names.</li>
 * <li>Fit: wardrobe ("for him", "women's"), sizes ("size M", "medium", "waist 32", "32 waist") and the
 * catalog's color names ("navy and olive"). Nothing is assumed when the text does not say it.</li>
 * </ol>
 */
public final class RuleBasedGoalInterpreter {

	public static final String SOURCE = "RULES";
	static final String CUSTOM_TITLE = "Custom bundle";
	private static final BigDecimal MAX_BUDGET = new BigDecimal("1000000");

	private static final Pattern DOLLAR_AMOUNT = Pattern
		.compile("\\$\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d{1,2}))?\\s*(k)?\\b");
	private static final Pattern CONTEXT_AMOUNT = Pattern.compile(
			"\\b(?:budget|under|below|max(?:imum)?|up to|around|about|spend(?:ing)?|less than|within)\\s*(?:of|is|:)?\\s*\\$?\\s*(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)\\s*(k)?\\b");
	private static final Pattern CURRENCY_WORD_AMOUNT = Pattern
		.compile("\\b(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(?:dollars|usd|bucks)\\b");

	/** Clauses are split at sentence ends and at words that start a new intent ("but I need..."). */
	private static final Pattern CLAUSE_SPLIT = Pattern.compile("[.;!?]|\\b(?:but|however|need|needs|want|wants|looking for)\\b");
	private static final Pattern OWNED_CLAUSE = Pattern
		.compile("\\b(?:already (?:have|own|got)|(?:i|we) (?:have|own) (?:a|an|my|our|some)|i've got|i have got"
				+ "|got (?:a|an|my|our|his|her|their)|gave me (?:a|an|my|his|her|their))\\b");

	private static final Pattern BUDGET_QUALITY = Pattern.compile(
			"\\b(?:cheap|cheapest|affordable|inexpensive|low[- ]cost|on a (?:tight )?budget|budget[- ]friendly|bargain)\\b");
	private static final Pattern PREMIUM_QUALITY = Pattern.compile(
			"\\b(?:premium|high[- ]end|top[- ]of[- ]the[- ]line|luxury|best (?:possible|quality)|no compromise|flagship)\\b");

	/** Usage phrase → product tag. */
	private static final Map<String, String> USAGE_PHRASES = new LinkedHashMap<>();

	static {
		phrases("casual", "casual", "relaxed", "laid back", "laid-back", "everyday", "easygoing", "comfortable", "comfy");
		phrases("streetwear", "streetwear", "street style", "street", "urban", "oversized", "sporty", "athleisure",
				"hype");
		phrases("formal", "formal", "smart", "sharp", "tailored", "elegant", "classy", "polished", "business",
				"professional");
		phrases("minimalist", "minimalist", "minimal", "clean", "simple", "neutral", "understated", "timeless");
		phrases("party-wear", "party wear", "partywear", "glam", "glamorous", "statement", "sparkly", "night out",
				"stylish", "bold");
	}

	private static void phrases(String tag, String... values) {
		for (String value : values) {
			USAGE_PHRASES.put(value, tag);
		}
	}

	private RuleBasedGoalInterpreter() {
	}

	public static GoalAnalysis interpret(String text, InterpreterVocabulary vocabulary) {
		String normalized = normalize(text);
		List<String> matched = new ArrayList<>();

		Optional<TemplateTerms> template = matchTemplate(normalized, vocabulary, matched);
		BigDecimal budget = extractBudget(normalized, matched);
		Quality quality = detectQuality(normalized);

		Set<String> templateCategories = new LinkedHashSet<>();
		template.ifPresent(t -> t.items().forEach(i -> templateCategories.add(i.categorySlug())));
		Map<String, RequirementKind> mentioned = mentionedCategories(normalized, vocabulary, templateCategories,
				matched);

		List<Item> items = buildItems(template, mentioned);
		List<String> usageTags = detectUsageTags(normalized, vocabulary, template, matched);
		List<String> brands = detectBrands(normalized, vocabulary, matched);
		Fit fit = detectFit(normalized, vocabulary, template, matched);

		List<String> clarifications = new ArrayList<>();
		if (budget == null) {
			clarifications.add("What is your total budget?");
		}
		if (items.stream().noneMatch(i -> i.kind() != RequirementKind.OWNED)) {
			clarifications.add("Which products do you need? For example: a desk, a chair and a monitor.");
		}

		return new GoalAnalysis(SOURCE, template.map(TemplateTerms::slug).orElse(null),
				template.map(TemplateTerms::title).orElse(CUSTOM_TITLE), budget, quality, items, usageTags, brands,
				matched.stream().distinct().toList(), clarifications, fit);
	}

	private static final Set<String> MEN_WORDS = Set.of("men", "man", "mens", "men's", "guy", "guys", "him", "his",
			"boyfriend", "husband", "male", "dad", "brother");
	private static final Set<String> WOMEN_WORDS = Set.of("women", "woman", "womens", "women's", "girl", "girls",
			"her", "girlfriend", "wife", "ladies", "female", "mom", "sister");

	private static final Pattern ALPHA_SIZE = Pattern
		.compile("\\b(?:size|sz|wear(?:s)? (?:a|an)?|in (?:a|an)?)\\s*(xxl|xl|xs|s|m|l|2xl|small|medium|large|extra large|extra small)\\b");
	private static final Pattern ALPHA_WORD_SIZE = Pattern.compile("\\b(extra small|extra large|small|medium|large)\\s+size\\b");
	private static final Pattern WAIST_SIZE = Pattern
		.compile("\\b(?:waist|w)\\s*(?:size\\s*)?(2[4-9]|3[0-9]|4[0-4])\\b|\\b(2[4-9]|3[0-9]|4[0-4])\\s*(?:inch\\s*)?waist\\b");
	private static final Pattern NUMERIC_SIZE = Pattern.compile("\\bsize\\s*(2[4-9]|3[0-9]|4[0-4])\\b");
	private static final Map<String, String> ALPHA_CANONICAL = Map.of("small", "S", "medium", "M", "large", "L",
			"extra large", "XL", "extra small", "XS", "2xl", "XXL");

	/** "men" for templates whose slug ends in -men, "women" for -women, otherwise null. */
	static String wardrobeOfTemplate(String templateSlug) {
		if (templateSlug == null) {
			return null;
		}
		return templateSlug.endsWith("-women") ? "WOMEN" : templateSlug.endsWith("-men") ? "MEN" : null;
	}

	private static Fit detectFit(String text, InterpreterVocabulary vocabulary, Optional<TemplateTerms> template,
			List<String> matched) {
		boolean men = MEN_WORDS.stream().anyMatch(w -> countOccurrences(text, w) > 0);
		boolean women = WOMEN_WORDS.stream().anyMatch(w -> countOccurrences(text, w) > 0);
		String gender = men == women ? wardrobeOfTemplate(template.map(TemplateTerms::slug).orElse(null))
				: men ? "MEN" : "WOMEN";

		String alpha = null;
		for (Pattern pattern : List.of(ALPHA_SIZE, ALPHA_WORD_SIZE)) {
			Matcher m = pattern.matcher(text);
			if (alpha == null && m.find()) {
				alpha = ALPHA_CANONICAL.getOrDefault(m.group(1), m.group(1).toUpperCase(Locale.ROOT));
				matched.add(m.group().trim());
			}
		}
		String waist = null;
		Matcher w = WAIST_SIZE.matcher(text);
		if (w.find()) {
			waist = w.group(1) != null ? w.group(1) : w.group(2);
			matched.add(w.group().trim());
		}
		else {
			Matcher n = NUMERIC_SIZE.matcher(text);
			if (n.find()) {
				waist = n.group(1);
				matched.add(n.group().trim());
			}
		}

		// colors in the order written, only names the catalog actually sells
		List<Hit> colorHits = new ArrayList<>();
		for (String color : vocabulary.colors()) {
			Matcher m = Pattern.compile("\\b" + Pattern.quote(normalize(color)) + "\\b").matcher(text);
			if (m.find()) {
				colorHits.add(new Hit(m.start(), color, color));
			}
		}
		colorHits.sort(Comparator.comparingInt(Hit::position));
		// "rose gold" also contains "gold": keep the longer name only
		List<String> colors = colorHits.stream()
			.map(Hit::slug)
			.filter(c -> colorHits.stream()
				.noneMatch(o -> !o.slug().equals(c) && normalize(o.slug()).contains(normalize(c))))
			.toList();
		colors.forEach(matched::add);
		return new Fit(gender, alpha, waist, colors);
	}

	static String normalize(String text) {
		// "work-from-home" and "work from home" should match the same keyword
		return text.toLowerCase(Locale.ROOT).replace('-', ' ').replace('’', '\'').replaceAll("\\s+", " ").trim();
	}

	/**
	 * Wardrobe words ("him", "women"...) tell men's and women's templates apart but never choose a template on
	 * their own: an occasion keyword must match, otherwise the goal stays custom.
	 */
	static final Set<String> WARDROBE_WORDS = Set.of("men", "man", "mens", "guy", "him", "boyfriend", "husband",
			"women", "woman", "womens", "girl", "her", "girlfriend", "wife", "ladies");

	private static Optional<TemplateTerms> matchTemplate(String text, InterpreterVocabulary vocabulary,
			List<String> matched) {
		TemplateTerms best = null;
		int bestScore = 0;
		int bestWardrobe = 0;
		List<String> bestTerms = List.of();
		for (TemplateTerms template : vocabulary.templates()) {
			List<String> terms = new ArrayList<>();
			int score = 0;
			int wardrobe = 0;
			for (String keyword : template.keywords()) {
				int occurrences = countOccurrences(text, normalize(keyword));
				if (occurrences > 0) {
					if (WARDROBE_WORDS.contains(normalize(keyword))) {
						wardrobe += occurrences;
					}
					else {
						// multi-word keywords are more specific than single words
						score += occurrences * keyword.split(" ").length;
					}
					terms.add(keyword);
				}
			}
			if (score > 0 && (score > bestScore || (score == bestScore && wardrobe > bestWardrobe))) {
				best = template;
				bestScore = score;
				bestWardrobe = wardrobe;
				bestTerms = terms;
			}
		}
		matched.addAll(bestTerms);
		return Optional.ofNullable(best);
	}

	static BigDecimal extractBudget(String text, List<String> matched) {
		for (Pattern pattern : List.of(DOLLAR_AMOUNT, CONTEXT_AMOUNT, CURRENCY_WORD_AMOUNT)) {
			Matcher m = pattern.matcher(text);
			while (m.find()) {
				BigDecimal amount = parseAmount(m, pattern);
				if (amount != null && amount.signum() > 0 && amount.compareTo(MAX_BUDGET) <= 0) {
					matched.add(m.group().trim());
					return amount.setScale(2, RoundingMode.HALF_UP);
				}
			}
		}
		return null;
	}

	private static BigDecimal parseAmount(Matcher m, Pattern pattern) {
		BigDecimal value = new BigDecimal(m.group(1).replace(",", ""));
		boolean thousands;
		if (pattern == DOLLAR_AMOUNT) {
			if (m.group(2) != null) {
				value = new BigDecimal(m.group(1).replace(",", "") + "." + m.group(2));
			}
			thousands = m.group(3) != null;
		}
		else if (pattern == CONTEXT_AMOUNT) {
			thousands = m.group(2) != null;
		}
		else {
			thousands = false;
		}
		return thousands ? value.multiply(BigDecimal.valueOf(1000)) : value;
	}

	private static Quality detectQuality(String text) {
		boolean budget = BUDGET_QUALITY.matcher(text).find();
		boolean premium = PREMIUM_QUALITY.matcher(text).find();
		if (budget == premium) {
			return Quality.BALANCED;
		}
		return budget ? Quality.BUDGET : Quality.PREMIUM;
	}

	/** Category slug → NEEDED (REQUIRED) or OWNED, in order of first mention. */
	private static Map<String, RequirementKind> mentionedCategories(String text, InterpreterVocabulary vocabulary,
			Set<String> templateCategories, List<String> matched) {
		// phrase → candidate categories, longest phrases first so "desk lamp" wins over "desk"
		Map<String, List<String>> phraseCandidates = new LinkedHashMap<>();
		vocabulary.categoryPhrases().forEach((slug, phrases) -> phrases
			.forEach(p -> phraseCandidates.computeIfAbsent(normalize(p), k -> new ArrayList<>()).add(slug)));
		List<String> orderedPhrases = phraseCandidates.keySet()
			.stream()
			.sorted(Comparator.comparingInt(String::length).reversed())
			.toList();

		Map<String, RequirementKind> result = new LinkedHashMap<>();
		for (String clause : CLAUSE_SPLIT.split(text)) {
			boolean owned = OWNED_CLAUSE.matcher(clause).find();
			StringBuilder remaining = new StringBuilder(clause);
			List<Hit> hits = new ArrayList<>();
			for (String phrase : orderedPhrases) {
				Matcher m = Pattern.compile("\\b" + Pattern.quote(phrase) + "(?:e?s)?\\b").matcher(remaining);
				while (m.find()) {
					hits.add(new Hit(m.start(), m.group(), resolve(phraseCandidates.get(phrase), templateCategories)));
					// blank out the match so shorter phrases inside it do not match again
					for (int i = m.start(); i < m.end(); i++) {
						remaining.setCharAt(i, ' ');
					}
				}
			}
			// report categories in the order the customer wrote them
			hits.sort(Comparator.comparingInt(Hit::position));
			for (Hit hit : hits) {
				matched.add(hit.text());
				record(result, hit.slug(), owned);
			}
		}
		return result;
	}

	private record Hit(int position, String text, String slug) {
	}

	private static void record(Map<String, RequirementKind> result, String slug, boolean owned) {
		RequirementKind kind = owned ? RequirementKind.OWNED : RequirementKind.REQUIRED;
		// "need" beats "own" if a category is mentioned both ways
		result.merge(slug, kind, (a, b) -> a == RequirementKind.REQUIRED || b == RequirementKind.REQUIRED
				? RequirementKind.REQUIRED : RequirementKind.OWNED);
	}

	private static String resolve(List<String> candidates, Set<String> templateCategories) {
		return candidates.stream().filter(templateCategories::contains).findFirst().orElse(candidates.getFirst());
	}

	private static List<Item> buildItems(Optional<TemplateTerms> template, Map<String, RequirementKind> mentioned) {
		Map<String, Item> items = new LinkedHashMap<>();
		template.ifPresent(t -> {
			for (TemplateItem ti : t.items()) {
				RequirementKind kind = mentioned.getOrDefault(ti.categorySlug(),
						ti.required() ? RequirementKind.REQUIRED : ti.selectedByDefault() ? RequirementKind.OPTIONAL : null);
				if (kind != null) {
					items.put(ti.categorySlug(), new Item(ti.categorySlug(), kind, ti.quantity()));
				}
			}
		});
		mentioned.forEach((slug, kind) -> items.putIfAbsent(slug, new Item(slug, kind, 1)));
		return List.copyOf(items.values());
	}

	private static List<String> detectUsageTags(String text, InterpreterVocabulary vocabulary,
			Optional<TemplateTerms> template, List<String> matched) {
		Set<String> allowed = template.<Set<String>>map(t -> new LinkedHashSet<>(t.usageTags())).orElse(vocabulary.tags());
		Set<String> tags = new LinkedHashSet<>();
		USAGE_PHRASES.forEach((phrase, tag) -> {
			if (allowed.contains(tag) && countOccurrences(text, phrase) > 0) {
				tags.add(tag);
				matched.add(phrase);
			}
		});
		return List.copyOf(tags);
	}

	private static List<String> detectBrands(String text, InterpreterVocabulary vocabulary, List<String> matched) {
		List<String> brands = new ArrayList<>();
		for (String brand : vocabulary.brands()) {
			if (countOccurrences(text, normalize(brand)) > 0) {
				brands.add(brand);
				matched.add(brand);
			}
		}
		return brands;
	}

	private static int countOccurrences(String text, String phrase) {
		Matcher m = Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b").matcher(text);
		int count = 0;
		while (m.find()) {
			count++;
		}
		return count;
	}
}
