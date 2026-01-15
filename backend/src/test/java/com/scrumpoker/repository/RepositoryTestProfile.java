package com.scrumpoker.repository;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that ensures repository integration tests always run against a
 * Testcontainers-managed PostgreSQL instance. This keeps the datasource
 * configuration isolated from local developer machines and mirrors CI.
 */
public class RepositoryTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        // Explicitly enable Dev Services so Testcontainers boots a clean Postgres
        config.put("quarkus.datasource.devservices.enabled", "true");
        // Pin the Postgres version used by tests to avoid drifting with local defaults
        config.put("quarkus.datasource.devservices.image-name", "docker.io/postgres:14");
        // Give the dev service a stable logical name for easier container inspection
        config.put("quarkus.datasource.devservices.service-name", "repository-tests-db");
        return config;
    }
}
