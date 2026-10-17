package com.buildmybundle.orders.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.buildmybundle.orders.OrderAdminService;
import com.buildmybundle.orders.OrderAdminService.AdminOrderSummary;
import com.buildmybundle.orders.OrderAdminService.AdminOrderView;
import com.buildmybundle.orders.domain.OrderStatus;
import com.buildmybundle.shared.error.ApiException;
import com.buildmybundle.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: orders")
@RestController
@RequestMapping("/api/v1/admin/orders")
class AdminOrderController {

	private static final int DEFAULT_PAGE_SIZE = 20;

	record StatusChange(@NotNull OrderStatus status) {
	}

	private final OrderAdminService orders;

	AdminOrderController(OrderAdminService orders) {
		this.orders = orders;
	}

	@Operation(summary = "All orders, newest first; filter by status or find by order number")
	@GetMapping
	PageResponse<AdminOrderSummary> list(@RequestParam(required = false) String status,
			@RequestParam(required = false) String orderNumber, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return orders.list(parseStatus(status), orderNumber, PageResponse.clampPage(page),
				PageResponse.clampSize(size, DEFAULT_PAGE_SIZE));
	}

	@Operation(summary = "An order with its customer and the status changes allowed next")
	@GetMapping("/{id}")
	AdminOrderView get(@PathVariable Long id) {
		return orders.get(id);
	}

	@Operation(summary = "Move an order to SHIPPED, DELIVERED or CANCELLED (cancelling restocks and refunds)")
	@PostMapping("/{id}/status")
	AdminOrderView changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChange change) {
		return orders.changeStatus(id, change.status());
	}

	private static OrderStatus parseStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		try {
			return OrderStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.badRequest("INVALID_STATUS", "Unknown order status: " + status);
		}
	}
}
