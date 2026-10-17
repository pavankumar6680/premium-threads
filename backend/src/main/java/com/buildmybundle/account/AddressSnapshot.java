package com.buildmybundle.account;

/** Immutable copy of an address, e.g. for stamping onto an order. */
public record AddressSnapshot(Long id, String fullName, String line1, String line2, String city, String state,
		String postalCode, String country, String phone, boolean defaultAddress) {
}
