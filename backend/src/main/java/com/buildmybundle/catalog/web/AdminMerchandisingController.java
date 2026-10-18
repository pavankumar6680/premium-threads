package com.buildmybundle.catalog.web;

import java.util.List;

import jakarta.validation.Valid;

import com.buildmybundle.catalog.admin.MerchandisingAdminService;
import com.buildmybundle.catalog.admin.MerchandisingAdminService.AdminTerm;
import com.buildmybundle.catalog.admin.MerchandisingAdminService.HomeSection;
import com.buildmybundle.catalog.admin.MerchandisingAdminService.HomeSectionRequest;
import com.buildmybundle.catalog.admin.MerchandisingAdminService.TermRequest;
import com.buildmybundle.catalog.admin.MerchandisingAdminService.Vocabulary;
import com.buildmybundle.catalog.admin.ProductImageService;
import com.buildmybundle.catalog.admin.ProductImageService.AdminImage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin: merchandising")
@RestController
@RequestMapping("/api/v1/admin")
class AdminMerchandisingController {

	private final MerchandisingAdminService merchandising;
	private final ProductImageService images;

	AdminMerchandisingController(MerchandisingAdminService merchandising, ProductImageService images) {
		this.merchandising = merchandising;
		this.images = images;
	}

	@GetMapping("/styles")
	List<AdminTerm> styles() {
		return merchandising.terms(Vocabulary.STYLE);
	}

	@PostMapping("/styles")
	@ResponseStatus(HttpStatus.CREATED)
	AdminTerm createStyle(@Valid @RequestBody TermRequest request) {
		return merchandising.createTerm(Vocabulary.STYLE, request);
	}

	@PutMapping("/styles/{id}")
	AdminTerm updateStyle(@PathVariable Long id, @Valid @RequestBody TermRequest request) {
		return merchandising.updateTerm(Vocabulary.STYLE, id, request);
	}

	@GetMapping("/occasions")
	List<AdminTerm> occasions() {
		return merchandising.terms(Vocabulary.OCCASION);
	}

	@PostMapping("/occasions")
	@ResponseStatus(HttpStatus.CREATED)
	AdminTerm createOccasion(@Valid @RequestBody TermRequest request) {
		return merchandising.createTerm(Vocabulary.OCCASION, request);
	}

	@PutMapping("/occasions/{id}")
	AdminTerm updateOccasion(@PathVariable Long id, @Valid @RequestBody TermRequest request) {
		return merchandising.updateTerm(Vocabulary.OCCASION, id, request);
	}

	@Operation(summary = "Homepage product rails (built-in and extra collections)")
	@GetMapping("/home-sections")
	List<HomeSection> homeSections() {
		return merchandising.homeSections(false);
	}

	@PostMapping("/home-sections")
	@ResponseStatus(HttpStatus.CREATED)
	HomeSection createHomeSection(@Valid @RequestBody HomeSectionRequest request) {
		return merchandising.createHomeSection(request);
	}

	@PutMapping("/home-sections/{id}")
	HomeSection updateHomeSection(@PathVariable Long id, @Valid @RequestBody HomeSectionRequest request) {
		return merchandising.updateHomeSection(id, request);
	}

	@DeleteMapping("/home-sections/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteHomeSection(@PathVariable Long id) {
		merchandising.deleteHomeSection(id);
	}

	@GetMapping("/products/{id}/images")
	List<AdminImage> images(@PathVariable Long id) {
		return images.list(id);
	}

	@Operation(summary = "Upload a product image (JPEG, PNG or WebP, at most 5 MB)")
	@PostMapping(path = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	List<AdminImage> upload(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@RequestParam(required = false) String altText, @RequestParam(required = false) String color) {
		return images.upload(id, file, altText, color);
	}

	@PutMapping("/products/{id}/images/{imageId}/primary")
	List<AdminImage> makePrimary(@PathVariable Long id, @PathVariable Long imageId) {
		return images.makePrimary(id, imageId);
	}

	@DeleteMapping("/products/{id}/images/{imageId}")
	List<AdminImage> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
		return images.delete(id, imageId);
	}
}
