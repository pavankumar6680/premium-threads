package com.buildmybundle.bundles.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.buildmybundle.auth.AuthenticatedUser;
import com.buildmybundle.bundles.BundleModels.AccessorySuggestion;
import com.buildmybundle.bundles.BundleModels.Alternative;
import com.buildmybundle.bundles.BundleModels.BundleSummary;
import com.buildmybundle.bundles.BundleModels.BundleView;
import com.buildmybundle.bundles.BundleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bundles")
@RestController
@RequestMapping("/api/v1")
class BundleController {

	private final BundleService bundles;

	BundleController(BundleService bundles) {
		this.bundles = bundles;
	}

	@Operation(summary = "A bundle with live prices, availability and a fresh compatibility check")
	@GetMapping("/bundles/{id}")
	BundleView get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
		return bundles.get(id, userId(user));
	}

	@Operation(summary = "Your saved and draft bundles")
	@GetMapping("/me/bundles")
	List<BundleSummary> mine(@AuthenticationPrincipal AuthenticatedUser user) {
		return bundles.mine(user.getId());
	}

	@Operation(summary = "Create your own editable copy of a bundle")
	@PostMapping("/bundles/{id}/customize")
	@ResponseStatus(HttpStatus.CREATED)
	BundleView customize(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
		return bundles.customize(id, user.getId());
	}

	@Operation(summary = "Products that could fill a slot, with price difference and compatibility verdict")
	@GetMapping("/bundles/{id}/alternatives")
	List<Alternative> alternatives(@PathVariable UUID id, @RequestParam String category) {
		return bundles.alternatives(id, category);
	}

	@Operation(summary = "Compatible accessories and the goal's other categories, with suggested products")
	@GetMapping("/bundles/{id}/accessories")
	List<AccessorySuggestion> accessories(@PathVariable UUID id) {
		return bundles.accessories(id);
	}

	@Operation(summary = "Replace the product in a category slot")
	@PutMapping("/bundles/{id}/items/{category}")
	BundleView replace(@PathVariable UUID id, @PathVariable String category,
			@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ReplaceRequest body) {
		return bundles.replace(id, user.getId(), category, body.productId());
	}

	@Operation(summary = "Change quantity or lock state of an item")
	@PatchMapping("/bundles/{id}/items/{category}")
	BundleView update(@PathVariable UUID id, @PathVariable String category,
			@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody UpdateRequest body) {
		return bundles.updateItem(id, user.getId(), category, body.quantity(), body.locked());
	}

	@Operation(summary = "Remove an optional item")
	@DeleteMapping("/bundles/{id}/items/{category}")
	BundleView remove(@PathVariable UUID id, @PathVariable String category,
			@AuthenticationPrincipal AuthenticatedUser user) {
		return bundles.removeItem(id, user.getId(), category);
	}

	@Operation(summary = "Add a product from a category the bundle does not have yet")
	@PostMapping("/bundles/{id}/items")
	BundleView add(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody AddRequest body) {
		return bundles.addItem(id, user.getId(), body.productId(), body.quantity() == null ? 1 : body.quantity());
	}

	@Operation(summary = "Change the bundle's budget")
	@PutMapping("/bundles/{id}/budget")
	BundleView budget(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody BudgetRequest body) {
		return bundles.setBudget(id, user.getId(), body.budget());
	}

	@Operation(summary = "Save the bundle under a name")
	@PostMapping("/bundles/{id}/save")
	BundleView save(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody SaveRequest body) {
		return bundles.save(id, user.getId(), body.name());
	}

	@DeleteMapping("/bundles/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
		bundles.delete(id, user.getId());
	}

	private static Long userId(AuthenticatedUser user) {
		return user == null ? null : user.getId();
	}

	record ReplaceRequest(@NotNull Long productId) {
	}

	record UpdateRequest(@Min(1) @Max(10) Integer quantity, Boolean locked) {
	}

	record AddRequest(@NotNull Long productId, @Min(1) @Max(10) Integer quantity) {
	}

	record BudgetRequest(
			@NotNull @DecimalMin("1.00") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2) BigDecimal budget) {
	}

	record SaveRequest(@NotBlank @Size(max = 120) String name) {
	}
}
