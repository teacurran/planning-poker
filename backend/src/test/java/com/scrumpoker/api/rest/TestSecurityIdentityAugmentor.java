package com.scrumpoker.api.rest;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final AtomicReference<UUID> CURRENT_USER_ID = new AtomicReference<>(TEST_USER_ID);

    public static void setTestUserId(UUID userId) {
        if (userId == null) {
            CURRENT_USER_ID.set(TEST_USER_ID);
        } else {
            CURRENT_USER_ID.set(userId);
        }
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        UUID principalIdValue = CURRENT_USER_ID.get();
        if (principalIdValue == null) {
            principalIdValue = TEST_USER_ID;
        }
        final UUID principalId = principalIdValue;

        return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
            .setAnonymous(false)
            .setPrincipal(() -> principalId.toString())
            .addRole("USER")
            .addAttribute("email", "test-user@example.com")
            .build());
    }
}
