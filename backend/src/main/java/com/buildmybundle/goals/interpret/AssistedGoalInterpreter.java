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

	/** Kept short on purpose: on a CPU, prompt length dominates the response time of a local model. */
	static final String SYSTEM_PROMPT = """
			Read a shopper's request for an online store and answer with one JSON object:
			{"templateSlug": the matching template slug or null,
			 "budget": the total budget the shopper stated as a number, or null,
			 "categories": [{"slug": category slug, "kind": "OWNED"}] for products the shopper says they already have,
			 "usageTags": usage tags that describe how they will use the products,
			 "clarifyingQuestions": [one short question only if the budget or the needed products are unclear]}
			Use only slugs and tags from the lists. Do not guess: leave out anything the shopper did not say.""";

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
		String categories = String.join(", ", vocab.categoryPhrases().keySet());
		return """
				Request: "%s"
				Templates: %s
				Categories: %s
				Usage tags: %s
				""".formatted(text.replace('"', '\''), templates, categories, String.join(", ", vocab.tags()));
	}
}
