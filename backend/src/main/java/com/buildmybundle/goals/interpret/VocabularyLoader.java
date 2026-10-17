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
		SYNONYMS.put("desk-lamps", List.of("desk lamp", "lamp", "lighting", "light", "task light"));
		SYNONYMS.put("desks", List.of("desk", "standing desk", "workstation desk"));
		SYNONYMS.put("chairs", List.of("chair", "office chair", "gaming chair", "seat"));
		SYNONYMS.put("monitor-arms", List.of("monitor arm", "monitor mount", "monitor stand"));
		SYNONYMS.put("monitors", List.of("monitor", "screen", "display"));
		SYNONYMS.put("keyboards", List.of("keyboard"));
		SYNONYMS.put("mice", List.of("mouse", "mice"));
		SYNONYMS.put("webcams", List.of("webcam", "web cam"));
		SYNONYMS.put("desktops", List.of("pc", "computer", "desktop", "gaming pc", "tower"));
		SYNONYMS.put("laptops", List.of("laptop", "notebook"));
		SYNONYMS.put("cpus", List.of("cpu", "processor"));
		SYNONYMS.put("motherboards", List.of("motherboard"));
		SYNONYMS.put("headphones", List.of("headphone", "headset", "earphone"));
		SYNONYMS.put("microphones", List.of("microphone", "mic"));
		SYNONYMS.put("audio-interfaces", List.of("audio interface", "interface", "mixer"));
		SYNONYMS.put("mic-arms", List.of("mic arm", "boom arm", "microphone arm"));
		SYNONYMS.put("acoustic-panels", List.of("acoustic panel", "acoustic treatment", "sound panel", "foam panel"));
		SYNONYMS.put("cameras", List.of("camera"));
		SYNONYMS.put("lenses", List.of("lens", "lenses"));
		SYNONYMS.put("tripods", List.of("tripod"));
		SYNONYMS.put("studio-lighting", List.of("studio light", "softbox", "ring light", "lighting", "light"));
		SYNONYMS.put("backdrops", List.of("backdrop", "background", "green screen"));
		SYNONYMS.put("memory-cards", List.of("memory card", "sd card"));
		SYNONYMS.put("dumbbells", List.of("dumbbell", "free weight", "weights"));
		SYNONYMS.put("kettlebells", List.of("kettlebell"));
		SYNONYMS.put("benches", List.of("bench", "weight bench"));
		SYNONYMS.put("exercise-mats", List.of("mat", "yoga mat", "gym flooring", "flooring"));
		SYNONYMS.put("resistance-bands", List.of("resistance band", "band"));
		SYNONYMS.put("cardio-machines", List.of("treadmill", "exercise bike", "rower", "rowing machine", "cardio", "walking pad"));
		SYNONYMS.put("smart-speakers", List.of("smart speaker", "speaker", "hub"));
		SYNONYMS.put("smart-lighting", List.of("smart bulb", "bulb", "smart light", "light strip", "lighting", "light"));
		SYNONYMS.put("smart-plugs", List.of("smart plug", "plug"));
		SYNONYMS.put("video-doorbells", List.of("doorbell", "video doorbell"));
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

		return new InterpreterVocabulary(terms, phrases, catalog.brands(), catalog.tags());
	}

}
