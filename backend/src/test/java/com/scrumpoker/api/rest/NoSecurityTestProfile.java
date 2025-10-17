package com.scrumpoker.api.rest;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that disables security for integration tests.
 * Authentication and authorization will be tested in Iteration 3.
 */
public class NoSecurityTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.http.auth.permission.permit-all.paths", "/*",
            "quarkus.http.auth.permission.permit-all.policy", "permit"
        );
    }
}
