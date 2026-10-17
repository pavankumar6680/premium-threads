package com.buildmybundle.admin;

import static com.buildmybundle.support.BrowserSession.longField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.buildmybundle.compatibility.CompatibilityService;
import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminRulesAndTemplatesIntegrationTest extends IntegrationTest {

	@Autowired
	CompatibilityService compatibility;

	private BrowserSession admin;
	private String suffix;

	@BeforeEach
	void setUp() throws Exception {
		admin = newAdmin();
		suffix = UUID.randomUUID().toString().substring(0, 6);
	}

	@Test
	void aNewRuleIsPreviewedThenEnforcedUntilDeactivated() throws Exception {
		String laptops = category("laptops-" + suffix, "outputs");
		String screens = category("screens-" + suffix, "inputs");
		long laptop = product(laptops, "outputs", "USB-C");
		long hdmiScreen = product(screens, "inputs", "HDMI");
		product(screens, "inputs", "USB-C");
		String rule = """
				{"code":"TEST_PORTS_%s","sourceCategory":"%s","targetCategory":"%s","operator":"LIST_INTERSECTS",
				 "sourceAttr":"outputs","targetAttr":"inputs","severity":"BLOCK","label":"shares a port",
				 "message":"The laptop cannot connect to this screen.","active":true}
				""".formatted(suffix.toUpperCase(), laptops, screens);

		admin.postJson("/api/v1/admin/compatibility-rules/preview", rule)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.evaluated").value(2))
			.andExpect(jsonPath("$.violating").value(1))
			.andExpect(jsonPath("$.examples[0]").value(org.hamcrest.Matchers.containsString("×")));
		assertThat(compatibility.check(List.of(laptop, hdmiScreen), null).compatible()).isTrue(); // preview saves nothing

		long id = longField(admin.postJson("/api/v1/admin/compatibility-rules", rule).andExpect(status().isCreated()), "$.id");
		assertThat(compatibility.check(List.of(laptop, hdmiScreen), null).compatible()).isFalse();

		admin.patchJson("/api/v1/admin/compatibility-rules/" + id + "/active", "{\"active\":false}")
			.andExpect(jsonPath("$.active").value(false));
		assertThat(compatibility.check(List.of(laptop, hdmiScreen), null).compatible()).isTrue();
		admin.postJson("/api/v1/admin/compatibility-rules", rule).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RULE_CODE_TAKEN"));
	}

	@Test
	void rulesMustReferToRealAttributes() throws Exception {
		String laptops = category("laptops-" + suffix, "outputs");
		admin.postJson("/api/v1/admin/compatibility-rules/preview", """
				{"code":"TEST_BAD","sourceCategory":"%s","targetCategory":"monitors","operator":"EQUALS",
				 "sourceAttr":"colour","targetAttr":"inputs","severity":"BLOCK","label":"x","message":"y","active":true}
				""".formatted(laptops))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RULE"))
			.andExpect(jsonPath("$.field").value("sourceAttr"));
		admin.postJson("/api/v1/admin/compatibility-rules", """
				{"code":"TEST_BAD2","sourceCategory":"%s","operator":"EQUALS",
				 "sourceAttr":"outputs","severity":"BLOCK","label":"x","message":"y","active":true}
				""".formatted(laptops))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("targetCategory"));
		admin.get("/api/v1/admin/compatibility-rules").andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(18)));
	}

	@Test
	void templatesCanBeCreatedAndRetired() throws Exception {
		String slug = "tiny-studio-" + suffix;
		String template = """
				{"slug":"%s","title":"Tiny studio %s","tagline":"Record anywhere","description":"",
				 "defaultBudget":400,"contextTag":"podcast","active":%s,
				 "items":[{"categorySlug":"microphones","required":true,"selectedByDefault":true,"quantity":1},
				          {"categorySlug":"headphones","required":false,"selectedByDefault":false,"quantity":1}],
				 "usageOptions":[{"tag":"podcast","label":"Podcasting"}],
				 "keywords":["voice memo studio %s"]}
				""";
		long id = longField(admin.postJson("/api/v1/admin/goal-templates", template.formatted(slug, suffix, true, suffix))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items[0].categorySlug").value("microphones"))
			.andExpect(jsonPath("$.items[1].selectedByDefault").value(false)), "$.id");

		BrowserSession visitor = new BrowserSession(mockMvc);
		visitor.get("/api/v1/goal-templates").andExpect(jsonPath("$[*].slug", hasItem(slug)));
		// the interpreter learns the new keyword without a restart
		visitor.postJson("/api/v1/goals/analyze", "{\"text\":\"I want a voice memo studio " + suffix + " for 400 dollars\"}")
			.andExpect(jsonPath("$.templateSlug").value(slug));

		admin.putJson("/api/v1/admin/goal-templates/" + id, template.formatted(slug, suffix, false, suffix))
			.andExpect(jsonPath("$.active").value(false));
		visitor.get("/api/v1/goal-templates").andExpect(jsonPath("$[*].slug", not(hasItem(slug))));
		admin.get("/api/v1/admin/goal-templates").andExpect(jsonPath("$[*].slug", hasItem(slug)));
	}

	@Test
	void templatesNeedARequiredItemAndKnownCategories() throws Exception {
		admin.postJson("/api/v1/admin/goal-templates", """
				{"slug":"t-%s","title":"T","tagline":"T","defaultBudget":100,"active":true,
				 "items":[{"categorySlug":"headphones","required":false,"selectedByDefault":true,"quantity":1}]}
				""".formatted(suffix)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NO_REQUIRED_ITEM"));
		admin.postJson("/api/v1/admin/goal-templates", """
				{"slug":"t-%s","title":"T","tagline":"T","defaultBudget":100,"active":true,
				 "items":[{"categorySlug":"spaceships","required":true,"selectedByDefault":true,"quantity":1}]}
				""".formatted(suffix)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY"));
		admin.postJson("/api/v1/admin/goal-templates", """
				{"slug":"home-office","title":"T","tagline":"T","defaultBudget":100,"active":true,
				 "items":[{"categorySlug":"desks","required":true,"selectedByDefault":true,"quantity":1}]}
				""").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEMPLATE_SLUG_TAKEN"));
	}

	private String category(String slug, String listAttribute) throws Exception {
		admin.postJson("/api/v1/admin/categories", "{\"departmentSlug\":\"home-office\",\"slug\":\"" + slug + "\",\"name\":\"" + slug + "\"}")
			.andExpect(status().isCreated());
		admin.postJson("/api/v1/admin/categories/" + slug + "/attributes",
				"{\"key\":\"" + listAttribute + "\",\"label\":\"Ports\",\"type\":\"LIST\"}").andExpect(status().isCreated());
		return slug;
	}

	private long product(String category, String attribute, String value) throws Exception {
		String sku = "R-" + UUID.randomUUID().toString().substring(0, 8);
		return longField(admin.postJson("/api/v1/admin/products", """
				{"categorySlug":"%s","sku":"%s","name":"Rule test %s","brand":"Testbrand","price":10,"qualityTier":2,
				 "active":true,"attributes":{"%s":["%s"]},"stock":5}
				""".formatted(category, sku, sku, attribute, value)).andExpect(status().isCreated()), "$.id");
	}
}
