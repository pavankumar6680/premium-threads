package com.buildmybundle.support;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * One real PostgreSQL server per test JVM, started from bundled binaries, so integration tests run
 * without Docker or a local database installation. Each JVM gets a fresh, throwaway cluster.
 */
public final class TestPostgres {

	private static EmbeddedPostgres instance;

	private TestPostgres() {
	}

	public static synchronized EmbeddedPostgres get() {
		if (instance == null) {
			try {
				instance = EmbeddedPostgres.builder().start();
			}
			catch (IOException ex) {
				throw new UncheckedIOException("Could not start embedded PostgreSQL", ex);
			}
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					instance.close();
				}
				catch (IOException ignored) {
					// JVM is exiting
				}
			}));
		}
		return instance;
	}

	public static String jdbcUrl() {
		return get().getJdbcUrl("postgres", "postgres");
	}
}
