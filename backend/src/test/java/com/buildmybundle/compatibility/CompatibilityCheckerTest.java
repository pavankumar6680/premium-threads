package com.buildmybundle.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.compatibility.CompatibilityModels.Operator;
import com.buildmybundle.compatibility.CompatibilityModels.Report;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.compatibility.CompatibilityModels.Severity;
import com.buildmybundle.compatibility.CompatibilityModels.Space;

import org.junit.jupiter.api.Test;

class CompatibilityCheckerTest {

	private static final Rule ARM_LOAD = rule("ARM_LOAD", "monitors", "monitor-arms", Operator.LTE, "weight_kg", "max_load_kg");
	private static final Rule PORTS = rule("PORTS", "monitors", "desktops", Operator.LIST_INTERSECTS, "inputs", "video_outputs");
	private static final Rule VESA = rule("VESA", "monitors", "monitor-arms", Operator.LIST_CONTAINS, "vesa", "vesa_patterns");
	private static final Rule MOUNT = rule("MOUNT", "lenses", "cameras", Operator.EQUALS, "mount", "mount");
	private static final Rule DESK_WIDTH = new Rule("DESK_WIDTH", "desks", null, Operator.LTE_SPACE_WIDTH, "width_cm", null,
			null, Severity.BLOCK, "fits", "too wide");
	private static final Rule NEEDS_INTERFACE = new Rule("NEEDS_INTERFACE", "microphones", "audio-interfaces",
			Operator.REQUIRES_CATEGORY_UNLESS, "connection", null, "USB", Severity.BLOCK, "via interface", "needs interface");

	@Test
	void numericLimit() {
		ProductFacts heavy = facts(1, "monitors", Map.of("weight_kg", List.of("8.4")));
		ProductFacts light = facts(2, "monitors", Map.of("weight_kg", List.of("5.8")));
		ProductFacts arm = facts(3, "monitor-arms", Map.of("max_load_kg", List.of("8")));

		assertThat(CompatibilityChecker.pairCompatible(heavy, arm, List.of(ARM_LOAD))).isFalse();
		assertThat(CompatibilityChecker.pairCompatible(arm, light, List.of(ARM_LOAD))).as("order does not matter").isTrue();
	}

	@Test
	void listOperators() {
		ProductFacts hdmiOnly = facts(1, "monitors", Map.of("inputs", List.of("HDMI"), "vesa", List.of("100x100")));
		ProductFacts dpPc = facts(2, "desktops", Map.of("video_outputs", List.of("DisplayPort", "USB-C")));
		ProductFacts hdmiPc = facts(3, "desktops", Map.of("video_outputs", List.of("HDMI", "DisplayPort")));
		ProductFacts arm = facts(4, "monitor-arms", Map.of("vesa_patterns", List.of("75x75", "100x100")));
		ProductFacts bigArm = facts(5, "monitor-arms", Map.of("vesa_patterns", List.of("200x200")));

		assertThat(CompatibilityChecker.pairCompatible(hdmiOnly, dpPc, List.of(PORTS))).isFalse();
		assertThat(CompatibilityChecker.pairCompatible(hdmiOnly, hdmiPc, List.of(PORTS))).isTrue();
		assertThat(CompatibilityChecker.pairCompatible(hdmiOnly, arm, List.of(VESA))).isTrue();
		assertThat(CompatibilityChecker.pairCompatible(hdmiOnly, bigArm, List.of(VESA))).isFalse();
	}

	@Test
	void missingDataIsNotTreatedAsAnIncompatibility() {
		ProductFacts unknownMount = facts(1, "lenses", Map.of());
		ProductFacts camera = facts(2, "cameras", Map.of("mount", List.of("FX")));

		assertThat(CompatibilityChecker.evaluatePair(MOUNT, unknownMount, camera)).isEmpty();
		assertThat(CompatibilityChecker.pairCompatible(unknownMount, camera, List.of(MOUNT))).isTrue();
	}

	@Test
	void spaceRulesOnlyApplyWhenTheDimensionIsKnown() {
		ProductFacts desk = facts(1, "desks", Map.of("width_cm", List.of("140")));

		assertThat(CompatibilityChecker.fitsSpace(desk, new Space(BigDecimal.valueOf(120), null), List.of(DESK_WIDTH))).isFalse();
		assertThat(CompatibilityChecker.fitsSpace(desk, new Space(BigDecimal.valueOf(140), null), List.of(DESK_WIDTH))).isTrue();
		assertThat(CompatibilityChecker.fitsSpace(desk, Space.UNKNOWN, List.of(DESK_WIDTH))).isTrue();
	}

	@Test
	void presenceRulesLookAtTheWholeBundle() {
		ProductFacts xlrMic = facts(1, "microphones", Map.of("connection", List.of("XLR")));
		ProductFacts usbMic = facts(2, "microphones", Map.of("connection", List.of("USB", "XLR")));
		ProductFacts interfaceBox = facts(3, "audio-interfaces", Map.of());

		assertThat(CompatibilityChecker.presenceViolations(List.of(xlrMic), List.of(NEEDS_INTERFACE))).hasSize(1);
		assertThat(CompatibilityChecker.presenceViolations(List.of(xlrMic, interfaceBox), List.of(NEEDS_INTERFACE))).isEmpty();
		assertThat(CompatibilityChecker.presenceViolations(List.of(usbMic), List.of(NEEDS_INTERFACE))).isEmpty();
	}

	@Test
	void reportListsPassedChecksAndViolations() {
		ProductFacts monitor = facts(1, "monitors", Map.of("weight_kg", List.of("7.9"), "vesa", List.of("100x100")));
		ProductFacts arm = facts(2, "monitor-arms", Map.of("max_load_kg", List.of("9"), "vesa_patterns", List.of("200x200")));

		Report report = CompatibilityChecker.report(List.of(monitor, arm), Space.UNKNOWN, List.of(ARM_LOAD, VESA));

		assertThat(report.compatible()).isFalse();
		assertThat(report.passed()).extracting(CompatibilityModels.Check::ruleCode).containsExactly("ARM_LOAD");
		assertThat(report.violations()).extracting(CompatibilityModels.Violation::ruleCode).containsExactly("VESA");
	}

	private static Rule rule(String code, String source, String target, Operator op, String sourceAttr, String targetAttr) {
		return new Rule(code, source, target, op, sourceAttr, targetAttr, null, Severity.BLOCK, code, code + " violated");
	}

	private static ProductFacts facts(long id, String category, Map<String, List<String>> attributes) {
		ProductSummary summary = new ProductSummary(id, "S" + id, "s" + id, "P" + id, "B", BigDecimal.TEN,
				BigDecimal.valueOf(4.5), 10, 2, category, category, true, 10);
		return new ProductFacts(summary, 1L, new LinkedHashMap<>(attributes), Set.of());
	}
}
