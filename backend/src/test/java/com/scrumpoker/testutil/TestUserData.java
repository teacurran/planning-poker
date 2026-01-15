package com.scrumpoker.testutil;

import com.scrumpoker.api.rest.TestSecurityIdentityAugmentor;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.UserRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;

import java.util.UUID;

/**
 * Utility helpers for ensuring the default test user exists when running integration tests.
 */
public final class TestUserData {

    private TestUserData() {
    }

    /**
     * Ensures the default test user exists in the database.
     *
     * @param userRepository User repository
     * @return Uni emitting the test user
     */
    public static Uni<User> ensureTestUser(UserRepository userRepository) {
        UUID userId = TestSecurityIdentityAugmentor.TEST_USER_ID;
        return Panache.withTransaction(() ->
            userRepository.findById(userId)
                .onItem().ifNull().switchTo(() ->
                    Panache.getSession()
                        .chain(session -> session.createNativeQuery("""
                                INSERT INTO "user" (user_id, email, oauth_provider, oauth_subject, display_name, subscription_tier)
                                VALUES (?1, ?2, ?3, ?4, ?5, ?6)
                            """)
                            .setParameter(1, userId)
                            .setParameter(2, "test-user@example.com")
                            .setParameter(3, "google")
                            .setParameter(4, "test-user")
                            .setParameter(5, "Test User")
                            .setParameter(6, SubscriptionTier.PRO_PLUS.name())
                            .executeUpdate()
                        )
                        .flatMap(ignore -> userRepository.findById(userId))
                )
                .onItem().transform(user -> {
                    user.subscriptionTier = SubscriptionTier.PRO_PLUS;
                    return user;
                })
                .flatMap(userRepository::persist)
        );
    }
}
