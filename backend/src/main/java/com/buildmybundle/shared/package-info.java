/**
 * Cross-cutting infrastructure shared by every module: error model, API configuration.
 * Declared OPEN so all of its sub-packages may be used by other modules.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.buildmybundle.shared;
