package com.buildmybundle.goals.interpret;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the rule-based interpreter knows, loaded from the database (templates, categories, brands,
 * tags). Keeping it a plain value makes the interpreter a pure function that is easy to test.
 *
 * @param categoryPhrases leaf category slug → phrases that name it (singular forms; plurals match too)
 * @param colors the catalog's color names (e.g. "Navy", "Olive")
 */
public record InterpreterVocabulary(List<TemplateTerms> templates, Map<String, List<String>> categoryPhrases,
		Set<String> brands, Set<String> tags, Set<String> colors) {

	public InterpreterVocabulary(List<TemplateTerms> templates, Map<String, List<String>> categoryPhrases,
			Set<String> brands, Set<String> tags) {
		this(templates, categoryPhrases, brands, tags, Set.of());
	}

	public record TemplateTerms(String slug, String title, List<String> keywords, List<TemplateItem> items,
			List<String> usageTags) {
	}

	public record TemplateItem(String categorySlug, boolean required, boolean selectedByDefault, int quantity) {
	}
}
