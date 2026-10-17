package com.buildmybundle.ai.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.ai.*: whether AI assistance is used and how long a single model call may take. */
@ConfigurationProperties("app.ai")
public record AiProperties(@DefaultValue("false") boolean enabled, @DefaultValue("60s") Duration timeout) {
}
