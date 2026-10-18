package com.buildmybundle.catalog.web;

import java.time.Duration;

import com.buildmybundle.catalog.admin.ProductImageService;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves uploaded product images from the media directory under /media (names are unique, so cache long). */
@Configuration
class MediaConfig implements WebMvcConfigurer {

	private final ProductImageService images;

	MediaConfig(ProductImageService images) {
		this.images = images;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler(ProductImageService.URL_PREFIX + "**")
			.addResourceLocations(images.mediaDir().toUri().toString())
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable());
	}
}
