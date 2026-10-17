package com.buildmybundle.account;

import static com.buildmybundle.support.BrowserSession.longField;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;

import org.junit.jupiter.api.Test;

class AddressIntegrationTest extends IntegrationTest {

	@Test
	void firstAddressBecomesDefaultAndDefaultCanMove() throws Exception {
		BrowserSession customer = newCustomer();
		long home = longField(customer.postJson("/api/v1/me/addresses", addressJson("Home", false))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.defaultAddress").value(true))
			.andExpect(jsonPath("$.country").value("US")), "$.id");

		long work = longField(customer.postJson("/api/v1/me/addresses", addressJson("Work", true))
			.andExpect(jsonPath("$.defaultAddress").value(true)), "$.id");

		customer.get("/api/v1/me/addresses")
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].id").value(work))
			.andExpect(jsonPath("$[1].defaultAddress").value(false));

		customer.post("/api/v1/me/addresses/" + home + "/default").andExpect(jsonPath("$.defaultAddress").value(true));
		customer.get("/api/v1/me/addresses") // default address is listed first
			.andExpect(jsonPath("$[0].id").value(home))
			.andExpect(jsonPath("$[0].defaultAddress").value(true))
			.andExpect(jsonPath("$[1].defaultAddress").value(false));
	}

	@Test
	void deletingTheDefaultPromotesAnotherAddress() throws Exception {
		BrowserSession customer = newCustomer();
		long first = longField(customer.postJson("/api/v1/me/addresses", addressJson("First", false)), "$.id");
		long second = longField(customer.postJson("/api/v1/me/addresses", addressJson("Second", false)), "$.id");

		customer.delete("/api/v1/me/addresses/" + first).andExpect(status().isNoContent());

		customer.get("/api/v1/me/addresses")
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(second))
			.andExpect(jsonPath("$[0].defaultAddress").value(true));
	}

	@Test
	void addressesArePrivateToTheirOwner() throws Exception {
		BrowserSession owner = newCustomer();
		long id = longField(owner.postJson("/api/v1/me/addresses", addressJson("Owner", false)), "$.id");

		BrowserSession other = newCustomer();
		other.putJson("/api/v1/me/addresses/" + id, addressJson("Hijack", false))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
		other.delete("/api/v1/me/addresses/" + id).andExpect(status().isNotFound());
		other.get("/api/v1/me/addresses").andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void validatesAddressFields() throws Exception {
		newCustomer().postJson("/api/v1/me/addresses", """
				{"fullName":"","line1":"x","city":"x","state":"x","postalCode":"!","country":"USA"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'fullName')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'postalCode')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'country')]").exists());
	}
}
