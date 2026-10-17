package com.buildmybundle.auth;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.buildmybundle.auth.domain.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the auth module for staff screens: who a user id belongs to. */
@Service
public class UserDirectory {

	public record UserContact(Long id, String email, String fullName) {
	}

	private final UserRepository users;

	public UserDirectory(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public Map<Long, UserContact> contacts(Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return users.findAllById(userIds.stream().distinct().toList())
			.stream()
			.map(u -> new UserContact(u.getId(), u.getEmail(), u.getFullName()))
			.collect(Collectors.toMap(UserContact::id, Function.identity()));
	}
}
