package com.buildmybundle.goals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.buildmybundle.catalog.CategoryInfo;
import com.buildmybundle.catalog.CategoryStats;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.goals.GoalModels.CreateGoal;
import com.buildmybundle.goals.GoalModels.GoalSnapshot;
import com.buildmybundle.goals.GoalModels.RequirementKind;
import com.buildmybundle.goals.GoalViews.CategoryOption;
import com.buildmybundle.goals.GoalViews.Estimate;
import com.buildmybundle.goals.GoalViews.GoalItem;
import com.buildmybundle.goals.GoalViews.GoalSummary;
import com.buildmybundle.goals.GoalViews.GoalView;
import com.buildmybundle.goals.GoalViews.Space;
import com.buildmybundle.goals.GoalViews.TemplateDetail;
import com.buildmybundle.goals.GoalViews.TemplateRef;
import com.buildmybundle.goals.GoalViews.TemplateSummary;
import com.buildmybundle.goals.domain.GoalRequirement;
import com.buildmybundle.goals.domain.ShoppingGoal;
import com.buildmybundle.goals.domain.ShoppingGoalRepository;
import com.buildmybundle.goals.interpret.GoalAnalysis;
import com.buildmybundle.goals.interpret.GoalInterpreter;
import com.buildmybundle.goals.templates.GoalTemplates;
import com.buildmybundle.goals.templates.GoalTemplates.Template;
import com.buildmybundle.shared.error.ApiException;
import com.buildmybundle.shared.web.PageResponse;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Goal templates, goal interpretation and saved shopping goals. */
@Service
@Transactional(readOnly = true)
public class GoalService {

	static final String CUSTOM_TITLE = "Custom bundle";
	private static final int DEFAULT_PAGE_SIZE = 10;

	private final ShoppingGoalRepository goals;
	private final GoalTemplates templates;
	private final ProductCatalog catalog;
	private final GoalInterpreter interpreter;

	public GoalService(ShoppingGoalRepository goals, GoalTemplates templates, ProductCatalog catalog,
			GoalInterpreter interpreter) {
		this.goals = goals;
		this.templates = templates;
		this.catalog = catalog;
		this.interpreter = interpreter;
	}

	// ---------------------------------------------------------------- templates and categories

	public List<TemplateSummary> templates() {
		List<Template> all = templates.findAllActive();
		CategoryLookup lookup = lookup(all.stream().flatMap(t -> t.items().stream()).map(GoalTemplates.Item::categoryId).toList());
		return all.stream().map(t -> new TemplateSummary(t.slug(), t.title(), t.tagline(), t.defaultBudget(),
				minimum(t.items().stream().filter(GoalTemplates.Item::required).map(i -> Map.entry(i.categoryId(), i.quantity())).toList(), lookup),
				t.items().stream().filter(GoalTemplates.Item::selectedByDefault).map(i -> lookup.info(i.categoryId()).name()).toList()))
			.toList();
	}

	public TemplateDetail template(String slug) {
		Template t = templates.findActiveBySlug(slug).orElseThrow(GoalService::templateNotFound);
		CategoryLookup lookup = lookup(t.items().stream().map(GoalTemplates.Item::categoryId).toList());
		return new TemplateDetail(t.slug(), t.title(), t.tagline(), t.description(), t.defaultBudget(),
				minimum(t.items().stream().filter(GoalTemplates.Item::required).map(i -> Map.entry(i.categoryId(), i.quantity())).toList(), lookup),
				t.usageOptions().stream().map(u -> new GoalViews.UsageOption(u.tag(), u.label())).toList(),
				t.items().stream().map(i -> new GoalViews.TemplateItem(lookup.option(i.categoryId()), i.required(),
						i.selectedByDefault(), i.quantity(), i.note())).toList());
	}

	/** Every leaf category with live availability, for adding items beyond a template. */
	public List<CategoryOption> categoryOptions() {
		List<Long> leafIds = catalog.categories().stream().filter(CategoryInfo::leaf).map(CategoryInfo::id).toList();
		CategoryLookup lookup = lookup(leafIds);
		return leafIds.stream().map(lookup::option).toList();
	}

	public GoalAnalysis analyze(String text) {
		return interpreter.interpret(text);
	}

	// ---------------------------------------------------------------- goals

	@Transactional
	public GoalView create(Long userId, CreateGoal command) {
		Template template = blank(command.templateSlug()) ? null
				: templates.findActiveBySlug(command.templateSlug().trim()).orElseThrow(GoalService::templateNotFound);

		Map<String, CategoryInfo> leaves = catalog.categories()
			.stream()
			.filter(CategoryInfo::leaf)
			.collect(Collectors.toMap(CategoryInfo::slug, Function.identity()));
		List<String> unknown = command.items().stream().map(CreateGoal.Item::categorySlug).filter(s -> !leaves.containsKey(s)).toList();
		if (!unknown.isEmpty()) {
			throw ApiException.badRequest("UNKNOWN_CATEGORY", "Unknown categories: " + String.join(", ", unknown))
				.with("categories", unknown);
		}
		Set<String> seen = new HashSet<>();
		List<String> duplicates = command.items().stream().map(CreateGoal.Item::categorySlug).filter(s -> !seen.add(s)).toList();
		if (!duplicates.isEmpty()) {
			throw ApiException.badRequest("DUPLICATE_CATEGORY", "Each category can only be listed once.")
				.with("categories", duplicates);
		}
		if (command.items().stream().allMatch(i -> i.kind() == RequirementKind.OWNED)) {
			throw ApiException.badRequest("NOTHING_TO_BUY", "Choose at least one product category to buy.");
		}

		Set<String> brands = canonical(command.preferredBrands(), catalog.brands(), "UNKNOWN_BRAND", "brands",
				"Unknown brands");
		Set<String> tags = canonical(command.usageTags(), catalog.tags(), "UNKNOWN_USAGE_TAG", "usageTags",
				"Unknown usage preferences");

		String title = !blank(command.title()) ? command.title().trim()
				: template != null ? template.title() : CUSTOM_TITLE;
		ShoppingGoal goal = new ShoppingGoal(userId, template == null ? null : template.id(), title,
				trimToNull(command.rawText()), command.budget(), command.quality(), command.spaceWidthCm(),
				command.spaceDepthCm(), trimToNull(command.notes()), brands, tags);
		for (CreateGoal.Item item : command.items()) {
			int quantity = item.kind() == RequirementKind.OWNED ? 1 : Math.max(1, item.quantity());
			goal.addRequirement(leaves.get(item.categorySlug()).id(), item.kind(), quantity);
		}
		return view(goals.save(goal));
	}

	public GoalView get(UUID id) {
		return view(goals.findById(id).orElseThrow(GoalService::goalNotFound));
	}

	public PageResponse<GoalSummary> listForUser(Long userId, Integer page, Integer size) {
		return PageResponse.from(goals.findByUserIdOrderByCreatedAtDesc(userId,
				PageRequest.of(PageResponse.clampPage(page), PageResponse.clampSize(size, DEFAULT_PAGE_SIZE))),
				g -> new GoalSummary(g.getId(), g.getTitle(), templateSlug(g.getTemplateId()), g.getBudget(),
						g.getQuality(), (int) g.getRequirements().stream().filter(r -> r.getKind() != RequirementKind.OWNED).count(),
						g.getCreatedAt()));
	}

	/** Goal inputs for the recommendation engine. */
	public Optional<GoalSnapshot> find(UUID id) {
		return goals.findById(id).map(g -> {
			Map<Long, CategoryInfo> byId = categoriesById();
			Optional<Template> template = g.getTemplateId() == null ? Optional.empty() : templates.findById(g.getTemplateId());
			return new GoalSnapshot(g.getId(), g.getUserId(), template.map(Template::slug).orElse(null),
					template.map(Template::contextTag).orElse(null), g.getTitle(),
					g.getBudget(), g.getQuality(),
					g.getRequirements().stream().map(r -> new GoalSnapshot.Requirement(r.getCategoryId(),
							byId.get(r.getCategoryId()).slug(), r.getKind(), r.getQuantity())).toList(),
					List.copyOf(g.getPreferredBrands()), List.copyOf(g.getUsageTags()), g.getSpaceWidthCm(),
					g.getSpaceDepthCm());
		});
	}

	private GoalView view(ShoppingGoal g) {
		List<GoalRequirement> requirements = g.getRequirements();
		CategoryLookup lookup = lookup(requirements.stream().map(GoalRequirement::getCategoryId).toList());
		List<GoalItem> items = requirements.stream()
			.map(r -> new GoalItem(lookup.option(r.getCategoryId()), r.getKind(), r.getQuantity()))
			.toList();

		List<Map.Entry<Long, Integer>> essentials = toBuy(requirements, false);
		List<Map.Entry<Long, Integer>> selected = toBuy(requirements, true);
		BigDecimal essentialsMinimum = minimum(essentials, lookup);
		BigDecimal selectedMinimum = minimum(selected, lookup);
		List<String> unavailable = selected.stream()
			.map(Map.Entry::getKey)
			.filter(id -> lookup.stats(id).inStockCount() == 0)
			.map(id -> lookup.info(id).name())
			.toList();
		Estimate estimate = new Estimate(essentialsMinimum, selectedMinimum,
				essentialsMinimum.compareTo(g.getBudget()) <= 0, selectedMinimum.compareTo(g.getBudget()) <= 0,
				unavailable);

		Template template = g.getTemplateId() == null ? null : templates.findById(g.getTemplateId()).orElse(null);
		Space space = g.getSpaceWidthCm() == null && g.getSpaceDepthCm() == null ? null
				: new Space(g.getSpaceWidthCm(), g.getSpaceDepthCm());
		return new GoalView(g.getId(), g.getTitle(), template == null ? null : new TemplateRef(template.slug(), template.title()),
				g.getRawText(), g.getBudget(), g.getQuality(), items, g.getPreferredBrands().stream().sorted().toList(),
				g.getUsageTags().stream().sorted().toList(), space, g.getNotes(), g.getCreatedAt(), estimate);
	}

	private static List<Map.Entry<Long, Integer>> toBuy(List<GoalRequirement> requirements, boolean includeOptional) {
		return requirements.stream()
			.filter(r -> r.getKind() == RequirementKind.REQUIRED
					|| (includeOptional && r.getKind() == RequirementKind.OPTIONAL))
			.map(r -> Map.entry(r.getCategoryId(), r.getQuantity()))
			.toList();
	}

	/** Cheapest in-stock price × quantity, summed; categories with nothing in stock contribute nothing. */
	private static BigDecimal minimum(List<Map.Entry<Long, Integer>> items, CategoryLookup lookup) {
		return items.stream()
			.map(e -> {
				BigDecimal min = lookup.stats(e.getKey()).minPrice();
				return min == null ? BigDecimal.ZERO : min.multiply(BigDecimal.valueOf(e.getValue()));
			})
			.reduce(BigDecimal.ZERO, BigDecimal::add)
			.setScale(2);
	}

	private String templateSlug(Long templateId) {
		return templateId == null ? null : templates.findById(templateId).map(Template::slug).orElse(null);
	}

	private Map<Long, CategoryInfo> categoriesById() {
		return catalog.categories().stream().collect(Collectors.toMap(CategoryInfo::id, Function.identity()));
	}

	private CategoryLookup lookup(Collection<Long> categoryIds) {
		List<Long> ids = categoryIds.stream().distinct().toList();
		return new CategoryLookup(categoriesById(), catalog.categoryStats(ids));
	}

	/** Maps user input case-insensitively onto the canonical spelling; rejects values that do not exist. */
	private static Set<String> canonical(List<String> values, Set<String> known, String code, String property,
			String message) {
		if (values == null || values.isEmpty()) {
			return Set.of();
		}
		Map<String, String> byLower = new LinkedHashMap<>();
		known.forEach(k -> byLower.put(k.toLowerCase(Locale.ROOT), k));
		Set<String> result = new LinkedHashSet<>();
		List<String> unknown = new ArrayList<>();
		for (String value : values) {
			String match = byLower.get(value.trim().toLowerCase(Locale.ROOT));
			if (match == null) {
				unknown.add(value);
			}
			else {
				result.add(match);
			}
		}
		if (!unknown.isEmpty()) {
			throw ApiException.badRequest(code, message + ": " + String.join(", ", unknown)).with(property, unknown);
		}
		return result;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static String trimToNull(String value) {
		return blank(value) ? null : value.trim();
	}

	private static ApiException templateNotFound() {
		return ApiException.notFound("TEMPLATE_NOT_FOUND", "This goal template does not exist.");
	}

	private static ApiException goalNotFound() {
		return ApiException.notFound("GOAL_NOT_FOUND", "This goal does not exist.");
	}

	private record CategoryLookup(Map<Long, CategoryInfo> infos, Map<Long, CategoryStats> statsById) {

		CategoryInfo info(Long id) {
			return infos.get(id);
		}

		CategoryStats stats(Long id) {
			return statsById.getOrDefault(id, CategoryStats.empty(id));
		}

		CategoryOption option(Long id) {
			CategoryInfo c = info(id);
			CategoryStats s = stats(id);
			return new CategoryOption(c.slug(), c.name(), c.departmentSlug(), c.departmentName(), s.inStockCount(),
					s.minPrice(), s.maxPrice(), s.brands());
		}
	}
}
