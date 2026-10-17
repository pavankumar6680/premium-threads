package com.buildmybundle;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build if a module reaches into another module's internal packages or modules form a cycle. */
class ModularityTest {

	@Test
	void moduleBoundariesAreRespected() {
		ApplicationModules.of(BuildMyBundleApplication.class).verify();
	}
}
