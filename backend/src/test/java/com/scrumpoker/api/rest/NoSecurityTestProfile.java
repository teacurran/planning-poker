package com.scrumpoker.api.rest;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Test profile that disables security for integration tests.
 * Uses TestSecurityIdentityAugmentor to automatically grant USER role to all requests.
 * This allows @RolesAllowed("USER") annotations to pass without actual authentication.
 *
 * Authentication and authorization will be tested in Iteration 3.
 */
public class NoSecurityTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        // Disable OIDC authentication
        config.put("quarkus.oidc.enabled", "false");
        // Allow unannotated endpoints (but annotated ones still need roles)
        config.put("quarkus.security.jaxrs.deny-unannotated-endpoints", "false");
        return config;
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        // Enable the TestSecurityIdentityAugmentor to automatically grant USER role
        return Set.of(TestSecurityIdentityAugmentor.class);
    }
}
