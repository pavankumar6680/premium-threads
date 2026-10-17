package com.buildmybundle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Runs the backend against an embedded PostgreSQL server (no Docker, no local install needed):
 * {@code ./mvnw spring-boot:test-run}. Data persists in {@code backend/.data/postgres} between runs.
 * Activates the {@code dev} profile, which bootstraps admin@buildmybundle.local / Admin12345.
 */
public class LocalDevApplication {

	public static void main(String[] args) {
		SpringApplication.from(BuildMyBundleApplication::main)
			.with(EmbeddedPostgresConfig.class)
			.withAdditionalProfiles("dev")
			.run(args);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class EmbeddedPostgresConfig {

		@Bean(destroyMethod = "close")
		EmbeddedPostgres embeddedPostgres() throws IOException {
			Path dataDir = Path.of(".data", "postgres").toAbsolutePath();
			Files.createDirectories(dataDir);
			return EmbeddedPostgres.builder()
				.setDataDirectory(dataDir)
				.setCleanDataDirectory(false)
				.setPort(5433)
				.start();
		}

		@Bean
		DataSource dataSource(EmbeddedPostgres postgres) {
			HikariDataSource dataSource = new HikariDataSource();
			dataSource.setJdbcUrl(postgres.getJdbcUrl("postgres", "postgres"));
			dataSource.setUsername("postgres");
			dataSource.setPassword("postgres");
			return dataSource;
		}
	}
}
