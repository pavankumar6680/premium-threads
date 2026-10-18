package com.buildmybundle.goals.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.buildmybundle.auth.AuthenticatedUser;
import com.buildmybundle.goals.GoalModels.CreateGoal;
import com.buildmybundle.goals.GoalModels.Quality;
import com.buildmybundle.goals.GoalModels.RequirementKind;
import com.buildmybundle.goals.GoalService;
import com.buildmybundle.goals.GoalViews.CategoryOption;
import com.buildmybundle.goals.GoalViews.GoalSummary;
import com.buildmybundle.goals.GoalViews.GoalView;
import com.buildmybundle.goals.GoalViews.TemplateDetail;
import com.buildmybundle.goals.GoalViews.TemplateSummary;
import com.buildmybundle.goals.interpret.GoalAnalysis;
import com.buildmybundle.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Goals")
@RestController
@RequestMapping("/api/v1")
class GoalController {

	private final GoalService goalService;

	GoalController(GoalService goalService) {
		this.goalService = goalService;
	}

	@Operation(summary = "Goal templates with the cheapest in-stock cost of their essentials")
	@GetMapping("/goal-templates")
	List<TemplateSummary> templates() {
		return goalService.templates();
	}

	@Operation(summary = "A goal template with its categories, live price ranges and usage options")
	@GetMapping("/goal-templates/{slug}")
	TemplateDetail template(@PathVariable String slug) {
		return goalService.template(slug);
	}

	@Operation(summary = "All product categories with live availability, for building custom goals")
	@GetMapping("/goal-categories")
	List<CategoryOption> categories() {
		return goalService.categoryOptions();
	}

	@Operation(summary = "Interpret a free-text goal into structured requirements",
			description = "Deterministic rules over the store's own vocabulary; the result pre-fills the goal wizard.")
	@PostMapping("/goals/analyze")
	GoalAnalysis analyze(@Valid @RequestBody AnalyzeRequest body) {
		return goalService.analyze(body.text());
	}

	@Operation(summary = "Create a shopping goal",
			description = "Works for anonymous visitors too; the returned id is an unguessable share link. "
					+ "Signed-in customers also see the goal under /me/goals.")
	@PostMapping("/goals")
	@ResponseStatus(HttpStatus.CREATED)
	GoalView create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateGoalRequest body) {
		return goalService.create(user == null ? null : user.getId(), body.toCommand());
	}

	@GetMapping("/goals/{id}")
	GoalView get(@PathVariable UUID id) {
		return goalService.get(id);
	}

	@GetMapping("/me/goals")
	PageResponse<GoalSummary> mine(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return goalService.listForUser(user.getId(), page, size);
	}

	record AnalyzeRequest(@NotBlank @Size(max = 2000) String text) {
	}

	record CreateGoalRequest(@Size(max = 60) String templateSlug, @Size(max = 120) String title,
			@Size(max = 2000) String rawText,
			@NotNull @DecimalMin("1.00") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2) BigDecimal budget,
			@NotNull Quality quality, @NotEmpty @Size(max = 20) List<@Valid @NotNull ItemRequest> items,
			@Size(max = 10) List<@NotBlank @Size(max = 80) String> preferredBrands,
			@Size(max = 10) List<@NotBlank @Size(max = 40) String> usageTags, @Valid SpaceRequest space,
			@Size(max = 500) String notes, @Valid FitRequest fit) {

		CreateGoal toCommand() {
			return new CreateGoal(templateSlug, title, rawText, budget, quality,
					items.stream().map(i -> new CreateGoal.Item(i.categorySlug(), i.kind(), i.quantity() == null ? 1 : i.quantity())).toList(),
					preferredBrands, usageTags, space == null ? null : space.widthCm(),
					space == null ? null : space.depthCm(), notes,
					fit == null ? null : new com.buildmybundle.goals.GoalModels.Fit(fit.gender(), fit.sizeAlpha(),
							fit.sizeWaist(), fit.colors() == null ? java.util.List.of() : fit.colors()));
		}
	}

	/** Who the outfit is for; every field optional. */
	record FitRequest(@jakarta.validation.constraints.Pattern(regexp = "MEN|WOMEN") String gender,
			@jakarta.validation.constraints.Pattern(regexp = "XXS|XS|S|M|L|XL|XXL|XXXL", message = "a letter size such as M") String sizeAlpha,
			@jakarta.validation.constraints.Pattern(regexp = "[0-9]{2}", message = "a waist size such as 32") String sizeWaist,
			@Size(max = 6) List<@NotBlank @Size(max = 40) String> colors) {
	}

	record ItemRequest(@NotBlank @Size(max = 80) String categorySlug, @NotNull RequirementKind kind,
			@Min(1) @Max(10) Integer quantity) {
	}

	record SpaceRequest(@DecimalMin("10") @DecimalMax("2000") @Digits(integer = 4, fraction = 1) BigDecimal widthCm,
			@DecimalMin("10") @DecimalMax("2000") @Digits(integer = 4, fraction = 1) BigDecimal depthCm) {
	}
}
