package com.scrumpoker.api.rest;
import com.scrumpoker.security.JwtClaims;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;
import java.util.UUID;

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

    public static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        JwtClaims claims = new JwtClaims(
            TEST_USER_ID,
            "test-user@example.com",
            List.of("USER"),
            "PRO"
        );

        return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
            .setAnonymous(false)
            .setPrincipal(() -> TEST_USER_ID.toString())
            .addRole("USER")
            .addAttribute("jwt.claims", claims)
            .build());
    }
}
