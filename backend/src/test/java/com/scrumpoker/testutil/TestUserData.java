package com.scrumpoker.testutil;

import com.scrumpoker.api.rest.TestSecurityIdentityAugmentor;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.UserRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;

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
        return Panache.withTransaction(() ->
            userRepository.findById(TestSecurityIdentityAugmentor.TEST_USER_ID)
                .onItem().ifNull().switchTo(() -> {
                    User user = new User();
                    user.userId = TestSecurityIdentityAugmentor.TEST_USER_ID;
                    user.email = "test-user@example.com";
                    user.oauthProvider = "google";
                    user.oauthSubject = "test-user";
                    user.displayName = "Test User";
                    user.subscriptionTier = SubscriptionTier.PRO;
                    return userRepository.persist(user);
                })
        );
    }
}
