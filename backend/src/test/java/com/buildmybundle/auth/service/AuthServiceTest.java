package com.buildmybundle.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import com.buildmybundle.auth.AuthenticatedUser;
import com.buildmybundle.auth.domain.Role;
import com.buildmybundle.auth.domain.RoleName;
import com.buildmybundle.auth.domain.RoleRepository;
import com.buildmybundle.auth.domain.User;
import com.buildmybundle.auth.domain.UserRepository;
import com.buildmybundle.shared.error.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	UserRepository users;

	@Mock
	RoleRepository roles;

	@Mock
	PasswordEncoder passwordEncoder;

	AuthService service;

	@BeforeEach
	void setUp() {
		service = new AuthService(users, roles, passwordEncoder);
	}

	@Test
	void registerCustomerHashesPasswordNormalizesEmailAndAssignsCustomerRole() {
		Role customer = Mockito.mock(Role.class);
		when(customer.getName()).thenReturn(RoleName.CUSTOMER);
		when(roles.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(customer));
		when(passwordEncoder.encode("Passw0rd!")).thenReturn("{bcrypt}hash");
		when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		AuthenticatedUser principal = service.registerCustomer("  Ada@Example.COM ", "Passw0rd!", " Ada ");

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hash");
		assertThat(saved.getValue().getFullName()).isEqualTo("Ada");
		assertThat(principal.getRoles()).containsExactly("CUSTOMER");
		assertThat(principal.getPassword()).as("credentials erased").isNull();
	}

	@Test
	void createUserRejectsExistingEmailWithConflict() {
		when(users.existsByEmail("taken@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.createUser("Taken@example.com", "Passw0rd!", "X", Set.of(RoleName.CUSTOMER)))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(ex.getCode()).isEqualTo("EMAIL_TAKEN");
			});
		verify(users, never()).saveAndFlush(any());
	}
}
