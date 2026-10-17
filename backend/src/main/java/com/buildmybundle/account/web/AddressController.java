package com.buildmybundle.account.web;

import java.util.List;

import com.buildmybundle.account.AddressBook;
import com.buildmybundle.account.AddressBook.AddressInput;
import com.buildmybundle.account.AddressSnapshot;
import com.buildmybundle.auth.AuthenticatedUser;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

@Tag(name = "Account")
@RestController
@RequestMapping("/api/v1/me/addresses")
class AddressController {

	private final AddressBook addressBook;

	AddressController(AddressBook addressBook) {
		this.addressBook = addressBook;
	}

	@GetMapping
	List<AddressSnapshot> list(@AuthenticationPrincipal AuthenticatedUser user) {
		return addressBook.list(user.getId());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AddressSnapshot create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AddressRequest body) {
		return addressBook.create(user.getId(), body.toInput());
	}

	@PutMapping("/{id}")
	AddressSnapshot update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
			@Valid @RequestBody AddressRequest body) {
		return addressBook.update(user.getId(), id, body.toInput());
	}

	@PostMapping("/{id}/default")
	AddressSnapshot makeDefault(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
		return addressBook.setDefault(user.getId(), id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
		addressBook.delete(user.getId(), id);
	}

	record AddressRequest(@NotBlank @Size(max = 120) String fullName, @NotBlank @Size(max = 200) String line1,
			@Size(max = 200) String line2, @NotBlank @Size(max = 100) String city,
			@NotBlank @Size(max = 100) String state,
			@NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 -]{1,9}$", message = "must be a valid postal code") String postalCode,
			@NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a 2-letter country code") String country,
			@Pattern(regexp = "^$|^[+0-9 ()-]{6,30}$", message = "must be a valid phone number") String phone,
			Boolean makeDefault) {

		AddressInput toInput() {
			return new AddressInput(fullName, line1, line2, city, state, postalCode, country, phone,
					Boolean.TRUE.equals(makeDefault));
		}
	}
}
