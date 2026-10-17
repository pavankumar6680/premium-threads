package com.buildmybundle.goals.web;

import java.util.List;

import jakarta.validation.Valid;

import com.buildmybundle.goals.templates.TemplateAdminService;
import com.buildmybundle.goals.templates.TemplateAdminService.AdminTemplate;
import com.buildmybundle.goals.templates.TemplateAdminService.TemplateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: goal templates")
@RestController
@RequestMapping("/api/v1/admin/goal-templates")
class AdminTemplateController {

	private final TemplateAdminService templates;

	AdminTemplateController(TemplateAdminService templates) {
		this.templates = templates;
	}

	@Operation(summary = "All goal templates, including inactive ones")
	@GetMapping
	List<AdminTemplate> list() {
		return templates.list();
	}

	@Operation(summary = "Create a goal template")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AdminTemplate create(@Valid @RequestBody TemplateRequest request) {
		return templates.create(request);
	}

	@Operation(summary = "Replace a goal template (items, usage options and keywords included)")
	@PutMapping("/{id}")
	AdminTemplate update(@PathVariable Long id, @Valid @RequestBody TemplateRequest request) {
		return templates.update(id, request);
	}
}
