package com.buildmybundle.auth.web;

import java.util.List;

import com.buildmybundle.auth.AuthenticatedUser;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class AuthDtos {

	private AuthDtos() {
	}

	record RegisterRequest(
			@NotBlank @Email @Size(max = 254) String email,
			// bcrypt only uses the first 72 bytes, so cap the length rather than silently truncating
			@NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
					message = "must contain at least one letter and one number") String password,
			@NotBlank @Size(max = 120) String fullName) {
	}

	record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {
	}

	record UserResponse(Long id, String email, String fullName, List<String> roles) {

		static UserResponse from(AuthenticatedUser user) {
			return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
					user.getRoles().stream().sorted().toList());
		}
	}
}
