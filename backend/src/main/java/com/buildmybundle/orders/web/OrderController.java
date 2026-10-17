package com.buildmybundle.orders.web;

import com.buildmybundle.auth.AuthenticatedUser;
import com.buildmybundle.orders.OrderService;
import com.buildmybundle.orders.OrderViews.OrderSummary;
import com.buildmybundle.orders.OrderViews.OrderView;
import com.buildmybundle.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders")
@RestController
@RequestMapping("/api/v1")
class OrderController {

	private final OrderService orderService;

	OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@Operation(summary = "Place an order from the cart (simulated payment)",
			description = "Requires an Idempotency-Key header; retrying with the same key returns the original order.")
	@PostMapping("/checkout")
	@ResponseStatus(HttpStatus.CREATED)
	OrderView checkout(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody CheckoutRequest body) {
		return orderService.checkout(user.getId(), idempotencyKey, body.addressId());
	}

	@GetMapping("/orders")
	PageResponse<OrderSummary> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return orderService.list(user.getId(), page, size);
	}

	@GetMapping("/orders/{id}")
	OrderView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
		return orderService.get(user.getId(), id);
	}

	@PostMapping("/orders/{id}/cancel")
	OrderView cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
		return orderService.cancel(user.getId(), id);
	}

	record CheckoutRequest(@NotNull Long addressId) {
	}
}
