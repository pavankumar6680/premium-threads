package com.buildmybundle.cart.web;

import java.util.List;
import java.util.UUID;

import com.buildmybundle.auth.AuthenticatedUser;
import com.buildmybundle.bundles.BundleService;
import com.buildmybundle.cart.CartLine;
import com.buildmybundle.cart.CartService;
import com.buildmybundle.cart.CartView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cart")
@RestController
@RequestMapping("/api/v1/cart")
class CartController {

	private final CartService cartService;
	private final BundleService bundles;

	CartController(CartService cartService, BundleService bundles) {
		this.cartService = cartService;
		this.bundles = bundles;
	}

	@GetMapping
	CartView view(@AuthenticationPrincipal AuthenticatedUser user) {
		return cartService.view(user.getId());
	}

	@PostMapping("/items")
	CartView add(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AddItemRequest body) {
		return cartService.addItem(user.getId(), body.productId(), body.quantity() == null ? 1 : body.quantity());
	}

	@Operation(summary = "Add every product of a bundle to the cart (all or nothing)")
	@PostMapping("/bundles/{bundleId}")
	CartView addBundle(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID bundleId) {
		List<CartLine> lines = bundles.purchaseLines(bundleId)
			.stream()
			.map(l -> new CartLine(l.productId(), l.quantity()))
			.toList();
		return cartService.addLines(user.getId(), lines);
	}

	@PutMapping("/items/{productId}")
	CartView setQuantity(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long productId,
			@Valid @RequestBody QuantityRequest body) {
		return cartService.setQuantity(user.getId(), productId, body.quantity());
	}

	@DeleteMapping("/items/{productId}")
	CartView remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long productId) {
		return cartService.removeItem(user.getId(), productId);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void clear(@AuthenticationPrincipal AuthenticatedUser user) {
		cartService.clear(user.getId());
	}

	record AddItemRequest(@NotNull Long productId, @Min(1) @Max(CartService.MAX_LINE_QUANTITY) Integer quantity) {
	}

	record QuantityRequest(@NotNull @Min(1) @Max(CartService.MAX_LINE_QUANTITY) Integer quantity) {
	}
}
