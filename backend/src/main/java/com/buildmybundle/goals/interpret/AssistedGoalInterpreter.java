package com.buildmybundle.goals.interpret;

import java.util.stream.Collectors;

import com.buildmybundle.ai.LanguageModel;
import com.buildmybundle.ai.LanguageModel.Format;

import org.springframework.stereotype.Component;

/**
 * The goal interpreter used by the application: deterministic rules first, then (when a local model is
 * available) the model's reading, validated and merged by {@link AiGoalMerger}. Without AI, or if the model
 * fails, times out or answers nonsense, the result is exactly the rule-based one.
 */
@Component
public class AssistedGoalInterpreter implements GoalInterpreter {

	static final String SYSTEM_PROMPT = """
			You turn a shopper's request into JSON for an online store. Use ONLY values from the lists you are given;
			never invent categories, brands, tags or templates. Answer with a single JSON object with these keys:
			  "templateSlug": one template slug or null,
			  "budget": the total budget as a number, or null if the shopper did not state one,
			  "quality": "BUDGET", "BALANCED" or "PREMIUM",
			  "categories": [{"slug": category slug, "kind": "REQUIRED" | "OPTIONAL" | "OWNED", "quantity": number}],
			  "usageTags": [usage tags that match how the shopper will use the products],
			  "preferredBrands": [brands the shopper asked for],
			  "clarifyingQuestions": [at most 2 short questions, only if something essential is unclear]
			Use "OWNED" for things the shopper already has, "REQUIRED" for things they need,
			"OPTIONAL" for things they would like if the budget allows.""";

	private final VocabularyLoader vocabulary;
	private final LanguageModel languageModel;

	public AssistedGoalInterpreter(VocabularyLoader vocabulary, LanguageModel languageModel) {
		this.vocabulary = vocabulary;
		this.languageModel = languageModel;
	}

	@Override
	public GoalAnalysis interpret(String text) {
		InterpreterVocabulary vocab = vocabulary.load();
		GoalAnalysis rules = RuleBasedGoalInterpreter.interpret(text, vocab);
		if (!languageModel.status().available()) {
			return rules;
		}
		return languageModel.complete(SYSTEM_PROMPT, userPrompt(text, vocab), Format.JSON)
			.flatMap(AiGoalMerger::parse)
			.map(ai -> AiGoalMerger.merge(rules, ai, text, vocab))
			.orElse(rules);
	}

	static String userPrompt(String text, InterpreterVocabulary vocab) {
		String templates = vocab.templates()
			.stream()
			.map(t -> t.slug() + " (" + t.title() + ")")
			.collect(Collectors.joining(", "));
		String categories = vocab.categoryPhrases()
			.entrySet()
			.stream()
			.map(e -> e.getKey() + " (" + String.join("/", e.getValue().subList(0, Math.min(3, e.getValue().size()))) + ")")
			.collect(Collectors.joining(", "));
		return """
				Shopper's request:
				\"\"\"%s\"\"\"

				Templates: %s
				Categories: %s
				Usage tags: %s
				Brands: %s
				""".formatted(text.replace("\"\"\"", "\""), templates, categories, String.join(", ", vocab.tags()),
				String.join(", ", vocab.brands()));
	}
}
