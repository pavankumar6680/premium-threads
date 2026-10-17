package com.buildmybundle.wishlist.web;

import java.util.List;

import com.buildmybundle.auth.AuthenticatedUser;
import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.wishlist.WishlistService;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wishlist")
@RestController
@RequestMapping("/api/v1/wishlist")
class WishlistController {

	private final WishlistService wishlist;

	WishlistController(WishlistService wishlist) {
		this.wishlist = wishlist;
	}

	@GetMapping
	List<ProductSummary> list(@AuthenticationPrincipal AuthenticatedUser user) {
		return wishlist.list(user.getId());
	}

	@PutMapping("/{productId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void add(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long productId) {
		wishlist.add(user.getId(), productId);
	}

	@DeleteMapping("/{productId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long productId) {
		wishlist.remove(user.getId(), productId);
	}
}
