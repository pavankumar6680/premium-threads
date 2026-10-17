package com.buildmybundle.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Address copied onto the order at checkout; later edits to the address book do not change it. */
@Embeddable
public record ShippingAddress(@Column(name = "ship_full_name", nullable = false, length = 120) String fullName,
		@Column(name = "ship_line1", nullable = false, length = 200) String line1,
		@Column(name = "ship_line2", length = 200) String line2,
		@Column(name = "ship_city", nullable = false, length = 100) String city,
		@Column(name = "ship_state", nullable = false, length = 100) String state,
		@Column(name = "ship_postal_code", nullable = false, length = 20) String postalCode,
		@Column(name = "ship_country", nullable = false, length = 2) String country,
		@Column(name = "ship_phone", length = 30) String phone) {
}
