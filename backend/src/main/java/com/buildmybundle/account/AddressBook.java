package com.buildmybundle.account;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.buildmybundle.account.domain.Address;
import com.buildmybundle.account.domain.AddressRepository;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user's shipping addresses. Every operation is scoped by user id, so one user can never read or
 * change another user's address (a foreign id simply yields 404).
 */
@Service
@Transactional
public class AddressBook {

	static final int MAX_ADDRESSES = 10;

	private final AddressRepository addresses;

	public AddressBook(AddressRepository addresses) {
		this.addresses = addresses;
	}

	@Transactional(readOnly = true)
	public List<AddressSnapshot> list(Long userId) {
		return addresses.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId)
			.stream()
			.map(AddressBook::snapshot)
			.toList();
	}

	@Transactional(readOnly = true)
	public Optional<AddressSnapshot> find(Long userId, Long addressId) {
		return addresses.findByIdAndUserId(addressId, userId).map(AddressBook::snapshot);
	}

	public AddressSnapshot create(Long userId, AddressInput input) {
		long existing = addresses.countByUserId(userId);
		if (existing >= MAX_ADDRESSES) {
			throw ApiException.conflict("ADDRESS_LIMIT_REACHED",
					"You can save up to " + MAX_ADDRESSES + " addresses.");
		}
		Address address = new Address(userId);
		apply(address, input);
		addresses.saveAndFlush(address);
		// the first address is always the default
		if (existing == 0 || input.makeDefault()) {
			makeDefault(userId, address);
		}
		return snapshot(address);
	}

	public AddressSnapshot update(Long userId, Long addressId, AddressInput input) {
		Address address = owned(userId, addressId);
		apply(address, input);
		if (input.makeDefault()) {
			makeDefault(userId, address);
		}
		return snapshot(address);
	}

	public AddressSnapshot setDefault(Long userId, Long addressId) {
		Address address = owned(userId, addressId);
		makeDefault(userId, address);
		return snapshot(address);
	}

	public void delete(Long userId, Long addressId) {
		Address address = owned(userId, addressId);
		boolean wasDefault = address.isDefaultAddress();
		addresses.delete(address);
		addresses.flush();
		if (wasDefault) {
			addresses.findFirstByUserIdAndIdNotOrderByCreatedAtDesc(userId, addressId)
				.ifPresent(next -> next.setDefaultAddress(true));
		}
	}

	private void makeDefault(Long userId, Address address) {
		addresses.clearDefaultExcept(userId, address.getId());
		address.setDefaultAddress(true);
	}

	private Address owned(Long userId, Long addressId) {
		return addresses.findByIdAndUserId(addressId, userId)
			.orElseThrow(() -> ApiException.notFound("ADDRESS_NOT_FOUND", "Address not found."));
	}

	private static void apply(Address address, AddressInput in) {
		address.update(in.fullName().trim(), in.line1().trim(), blankToNull(in.line2()), in.city().trim(),
				in.state().trim(), in.postalCode().trim().toUpperCase(Locale.ROOT),
				in.country().toUpperCase(Locale.ROOT), blankToNull(in.phone()));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static AddressSnapshot snapshot(Address a) {
		return new AddressSnapshot(a.getId(), a.getFullName(), a.getLine1(), a.getLine2(), a.getCity(), a.getState(),
				a.getPostalCode(), a.getCountry(), a.getPhone(), a.isDefaultAddress());
	}

	public record AddressInput(String fullName, String line1, String line2, String city, String state,
			String postalCode, String country, String phone, boolean makeDefault) {
	}
}
