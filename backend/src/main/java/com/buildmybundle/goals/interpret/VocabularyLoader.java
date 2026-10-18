package com.buildmybundle.goals.interpret;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.buildmybundle.catalog.CategoryInfo;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.goals.interpret.InterpreterVocabulary.TemplateItem;
import com.buildmybundle.goals.interpret.InterpreterVocabulary.TemplateTerms;
import com.buildmybundle.goals.templates.GoalTemplates;
import com.buildmybundle.goals.templates.GoalTemplates.UsageOption;

import org.springframework.stereotype.Component;

/**
 * Builds the interpreter's vocabulary from live data: templates and their keywords, category names plus
 * common synonyms, catalog brands and tags. A new category or brand is understood without code changes;
 * the synonyms below only add everyday words the category names do not contain.
 */
@Component
public class VocabularyLoader {

	/** Extra phrases per category, listed in priority order for ambiguous words such as "lighting". */
	static final Map<String, List<String>> SYNONYMS = new LinkedHashMap<>();

	static {
		SYNONYMS.put("men-tshirts", List.of("t-shirt", "t shirt", "tshirt", "tee", "polo", "graphic tee"));
		SYNONYMS.put("men-shirts", List.of("shirt", "oxford", "button-down", "button down", "linen shirt", "dress shirt"));
		SYNONYMS.put("men-jeans", List.of("jeans", "denim", "jean"));
		SYNONYMS.put("men-trousers", List.of("trousers", "chinos", "pants", "joggers", "slacks", "track pants"));
		SYNONYMS.put("men-jackets", List.of("jacket", "blazer", "bomber", "windbreaker", "coat"));
		SYNONYMS.put("men-hoodies", List.of("hoodie", "sweatshirt", "pullover"));
		SYNONYMS.put("men-sweaters", List.of("sweater", "jumper", "knit", "cardigan", "knitwear"));
		SYNONYMS.put("men-ethnic-wear", List.of("kurta", "ethnic wear", "sherwani", "bandhgala", "traditional"));
		SYNONYMS.put("women-dresses", List.of("dress", "gown", "midi", "maxi", "sundress"));
		SYNONYMS.put("women-tops", List.of("top", "blouse", "tee", "t-shirt", "cami", "camisole", "crop top", "tank"));
		SYNONYMS.put("women-jeans", List.of("jeans", "denim", "jean"));
		SYNONYMS.put("women-trousers", List.of("trousers", "pants", "joggers", "culottes", "palazzo"));
		SYNONYMS.put("women-skirts", List.of("skirt", "mini skirt", "midi skirt"));
		SYNONYMS.put("women-jackets", List.of("jacket", "blazer", "trench", "puffer", "coat"));
		SYNONYMS.put("women-hoodies", List.of("hoodie", "sweatshirt"));
		SYNONYMS.put("women-ethnic-wear", List.of("kurta", "ethnic wear", "anarkali", "saree", "lehenga", "traditional"));
		SYNONYMS.put("watches", List.of("watch", "timepiece", "chronograph"));
		SYNONYMS.put("sunglasses", List.of("sunglasses", "shades", "sunnies"));
		SYNONYMS.put("handbags", List.of("handbag", "bag", "purse", "tote", "clutch", "crossbody"));
		SYNONYMS.put("backpacks", List.of("backpack", "rucksack", "gym bag"));
		SYNONYMS.put("wallets", List.of("wallet", "card holder", "cardholder"));
		SYNONYMS.put("belts", List.of("belt"));
		SYNONYMS.put("jewelry", List.of("jewelry", "jewellery", "necklace", "earrings", "bracelet", "ring", "pendant"));
		SYNONYMS.put("caps", List.of("cap", "hat", "beanie", "baseball cap"));
		SYNONYMS.put("scarves", List.of("scarf", "stole", "muffler"));
	}

	private final GoalTemplates templates;
	private final ProductCatalog catalog;

	public VocabularyLoader(GoalTemplates templates, ProductCatalog catalog) {
		this.templates = templates;
		this.catalog = catalog;
	}

	public InterpreterVocabulary load() {
		List<CategoryInfo> leaves = catalog.categories().stream().filter(CategoryInfo::leaf).toList();
		Map<Long, String> slugById = leaves.stream().collect(Collectors.toMap(CategoryInfo::id, CategoryInfo::slug));

		Map<String, List<String>> phrases = new LinkedHashMap<>();
		SYNONYMS.forEach((slug, words) -> {
			if (slugById.containsValue(slug)) {
				phrases.put(slug, new ArrayList<>(words));
			}
		});
		for (CategoryInfo leaf : leaves) {
			List<String> list = phrases.computeIfAbsent(leaf.slug(), k -> new ArrayList<>());
			String name = leaf.name().toLowerCase(Locale.ROOT);
			if (!list.contains(name)) {
				list.add(name);
			}
		}

		List<TemplateTerms> terms = templates.findAllActive()
			.stream()
			.map(t -> new TemplateTerms(t.slug(), t.title(), t.keywords(),
					t.items()
						.stream()
						.filter(i -> slugById.containsKey(i.categoryId()))
						.map(i -> new TemplateItem(slugById.get(i.categoryId()), i.required(), i.selectedByDefault(),
								i.quantity()))
						.toList(),
					t.usageOptions().stream().map(UsageOption::tag).toList()))
			.toList();

		return new InterpreterVocabulary(terms, phrases, catalog.brands(), catalog.tags(), catalog.colorNames());
	}

}
