package com.buildmybundle.compatibility.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.buildmybundle.compatibility.RuleAdminService;
import com.buildmybundle.compatibility.RuleAdminService.AdminRule;
import com.buildmybundle.compatibility.RuleAdminService.RuleImpact;
import com.buildmybundle.compatibility.RuleAdminService.RuleRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: compatibility rules")
@RestController
@RequestMapping("/api/v1/admin/compatibility-rules")
class AdminRuleController {

	record ActiveRequest(@NotNull Boolean active) {
	}

	private final RuleAdminService rules;

	AdminRuleController(RuleAdminService rules) {
		this.rules = rules;
	}

	@Operation(summary = "All compatibility rules, active and inactive")
	@GetMapping
	List<AdminRule> list() {
		return rules.list();
	}

	@Operation(summary = "Create a rule (takes effect immediately when active)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AdminRule create(@Valid @RequestBody RuleRequest request) {
		return rules.create(request);
	}

	@Operation(summary = "Update a rule")
	@PutMapping("/{id}")
	AdminRule update(@PathVariable Long id, @Valid @RequestBody RuleRequest request) {
		return rules.update(id, request);
	}

	@Operation(summary = "Activate or deactivate a rule")
	@PatchMapping("/{id}/active")
	AdminRule setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
		return rules.setActive(id, request.active());
	}

	@Operation(summary = "Preview a rule's effect on the active catalog without saving it")
	@PostMapping("/preview")
	RuleImpact preview(@Valid @RequestBody RuleRequest request) {
		return rules.preview(request);
	}
}
