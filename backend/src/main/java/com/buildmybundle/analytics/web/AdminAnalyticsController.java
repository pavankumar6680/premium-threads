package com.buildmybundle.analytics.web;

import java.util.Set;

import com.buildmybundle.analytics.AnalyticsService;
import com.buildmybundle.analytics.AnalyticsService.Summary;
import com.buildmybundle.shared.audit.AuditLog;
import com.buildmybundle.shared.audit.AuditLog.Entry;
import com.buildmybundle.shared.error.ApiException;
import com.buildmybundle.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: analytics")
@RestController
@RequestMapping("/api/v1/admin")
class AdminAnalyticsController {

	private static final Set<Integer> PERIODS = Set.of(7, 30, 90);
	private static final int DEFAULT_PAGE_SIZE = 30;

	private final AnalyticsService analytics;
	private final AuditLog audit;

	AdminAnalyticsController(AnalyticsService analytics, AuditLog audit) {
		this.analytics = analytics;
		this.audit = audit;
	}

	@Operation(summary = "Dashboard summary: sales, inventory, goals and recommendation engine health",
			description = "days: 7, 30 or 90. Revenue excludes cancelled orders.")
	@GetMapping("/analytics/summary")
	Summary summary(@RequestParam(defaultValue = "30") int days) {
		if (!PERIODS.contains(days)) {
			throw ApiException.badRequest("INVALID_PERIOD", "days must be 7, 30 or 90.");
		}
		return analytics.summary(days);
	}

	@Operation(summary = "Audit log of admin changes, newest first",
			description = "entityType: PRODUCT, CATEGORY, ORDER, COMPATIBILITY_RULE or GOAL_TEMPLATE.")
	@GetMapping("/audit-log")
	PageResponse<Entry> auditLog(@RequestParam(required = false) String entityType,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return audit.recent(entityType == null || entityType.isBlank() ? null : entityType,
				PageResponse.clampPage(page), PageResponse.clampSize(size, DEFAULT_PAGE_SIZE));
	}
}
