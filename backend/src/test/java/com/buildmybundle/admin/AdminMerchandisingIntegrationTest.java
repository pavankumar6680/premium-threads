package com.buildmybundle.admin;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AdminMerchandisingIntegrationTest extends IntegrationTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R' };

	private BrowserSession admin;

	@BeforeEach
	void setUp() throws Exception {
		admin = newAdmin();
	}

	@Test
	void stylesAndOccasionsAreManagedWithoutCodeChanges() throws Exception {
		String name = "Coastal " + UUID.randomUUID().toString().substring(0, 6);
		String created = admin.postJson("/api/v1/admin/styles", "{\"name\":\"" + name + "\",\"description\":\"Linen and light tones\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value(startsWith("coastal-")))
			.andExpect(jsonPath("$.active").value(true))
			.andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(created, "$.id");
		new BrowserSession(mockMvc).get("/api/v1/vocabulary").andExpect(jsonPath("$.styles[*].name", hasItem(name)));

		admin.putJson("/api/v1/admin/styles/" + id, "{\"name\":\"" + name + "\",\"active\":false}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
		new BrowserSession(mockMvc).get("/api/v1/vocabulary").andExpect(jsonPath("$.styles[*].name", not(hasItem(name))));
		admin.postJson("/api/v1/admin/styles", "{\"name\":\"" + name + "\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TERM_TAKEN"));

		admin.get("/api/v1/admin/occasions").andExpect(status().isOk()).andExpect(jsonPath("$[*].slug", hasItem("office")));
		newCustomer().get("/api/v1/admin/styles").andExpect(status().isForbidden());
	}

	@Test
	void homepageRailsAreConfigurable() throws Exception {
		new BrowserSession(mockMvc).get("/api/v1/home/sections")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].slug").value("trending"))
			.andExpect(jsonPath("$[0].builtIn").value(true));

		String slug = "edit-" + UUID.randomUUID().toString().substring(0, 6);
		String created = admin.postJson("/api/v1/admin/home-sections",
				"{\"slug\":\"" + slug + "\",\"eyebrow\":\"Edit\",\"title\":\"The streetwear edit\",\"query\":\"?style=streetwear&gender=WOMEN\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.query").value("style=streetwear&gender=WOMEN"))
			.andExpect(jsonPath("$.builtIn").value(false))
			.andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(created, "$.id");
		new BrowserSession(mockMvc).get("/api/v1/home/sections").andExpect(jsonPath("$[*].slug", hasItem(slug)));

		admin.postJson("/api/v1/admin/home-sections", "{\"title\":\"Bad\",\"query\":\"page=2&evil=1\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SECTION_QUERY"));

		String trendingId = JsonPath.<java.util.List<Number>>read(
				admin.get("/api/v1/admin/home-sections").andReturn().getResponse().getContentAsString(),
				"$[?(@.slug == 'trending')].id").getFirst().toString();
		admin.delete("/api/v1/admin/home-sections/" + trendingId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SECTION_BUILT_IN"));
		admin.delete("/api/v1/admin/home-sections/" + id).andExpect(status().isNoContent());
	}

	@Test
	void productImagesAreUploadedValidatedAndServed() throws Exception {
		long productId = createProduct("Image Tee " + UUID.randomUUID().toString().substring(0, 6), "20.00", 5);

		String first = admin.multipart("/api/v1/admin/products/" + productId + "/images",
				new MockMultipartFile("file", "front.png", "image/png", PNG))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$[0].uploaded").value(true))
			.andExpect(jsonPath("$[0].url").value(startsWith("/media/products/" + productId + "-")))
			.andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(first, "$[0].url");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
			.andExpect(status().isOk());

		// the declared type does not matter, the content does
		admin.multipart("/api/v1/admin/products/" + productId + "/images",
				new MockMultipartFile("file", "evil.png", "image/png", "<script>alert(1)</script>".getBytes()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IMAGE_TYPE"));

		String second = admin.multipart("/api/v1/admin/products/" + productId + "/images",
				new MockMultipartFile("file", "back.png", "image/png", PNG))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		Number secondId = JsonPath.read(second, "$[1].id");
		admin.put("/api/v1/admin/products/" + productId + "/images/" + secondId + "/primary")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(secondId));
		admin.delete("/api/v1/admin/products/" + productId + "/images/" + secondId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1));
	}
}
