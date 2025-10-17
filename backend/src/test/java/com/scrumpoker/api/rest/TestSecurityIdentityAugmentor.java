package com.scrumpoker.api.rest;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Test-only security identity augmentor for integration tests.
 * Automatically grants the USER role to all requests during tests.
 * This allows @RolesAllowed("USER") annotations to pass without actual authentication.
 *
 * This augmentor is activated only when using NoSecurityTestProfile.
 * Authentication and authorization will be properly tested in Iteration 3.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestSecurityIdentityAugmentor implements SecurityIdentityAugmentor {

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        // Always create a non-anonymous identity with USER role
        // This bypasses @RolesAllowed("USER") security checks in controllers
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
            .setAnonymous(false)
            .setPrincipal(() -> "test-user")
            .addRole("USER")
            .build());
    }
}
