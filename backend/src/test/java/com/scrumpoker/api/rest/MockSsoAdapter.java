package com.scrumpoker.api.rest;

import com.scrumpoker.integration.sso.SsoAdapter;
import com.scrumpoker.integration.sso.SsoUserInfo;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.UUID;

/**
 * Mock implementation of SsoAdapter for integration testing.
 * <p>
 * This mock does NOT extend SsoAdapter to avoid inheriting @Inject dependencies
 * (OidcProvider, ObjectMapper) that may cause issues in the test environment.
 * Instead, it provides a minimal implementation that returns configured responses.
 * </p>
 * <p>
 * Usage in tests:
 * <pre>
 *   @Inject
 *   MockSsoAdapter mockSsoAdapter;
 *
 *   // Configure successful response
 *   SsoUserInfo userInfo = new SsoUserInfo(...);
 *   mockSsoAdapter.configureMockSuccess(userInfo);
 *
 *   // Or configure failure
 *   mockSsoAdapter.configureMockFailure("Invalid token");
 * </pre>
 * </p>
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class MockSsoAdapter extends SsoAdapter {

    private volatile SsoUserInfo mockUserInfo = null;
    private volatile boolean shouldThrowException = false;
    private volatile String exceptionMessage = null;

    /**
     * Configure the mock to return successful authentication with the given user info.
     */
    public void configureMockSuccess(SsoUserInfo userInfo) {
        this.mockUserInfo = userInfo;
        this.shouldThrowException = false;
        this.exceptionMessage = null;
    }

    /**
     * Configure the mock to throw an exception with the given message.
     */
    public void configureMockFailure(String errorMessage) {
        this.shouldThrowException = true;
        this.exceptionMessage = errorMessage;
        this.mockUserInfo = null;
    }

    /**
     * Reset mock to default state (no behavior configured).
     */
    public void reset() {
        this.mockUserInfo = null;
        this.shouldThrowException = false;
        this.exceptionMessage = null;
    }

    /**
     * Mock authenticate method that returns configured responses.
     * Overrides SsoAdapter.authenticate() to return mocked data without calling real IdP.
     */
    @Override
    public Uni<SsoUserInfo> authenticate(String ssoConfigJson, String authCode,
                                          SsoAuthParams params, UUID orgId) {
        // Return mocked response based on configuration
        if (shouldThrowException) {
            return Uni.createFrom().failure(new IllegalArgumentException(exceptionMessage));
        }

        if (mockUserInfo != null) {
            // Create a new SsoUserInfo with the orgId from the call (for proper org assignment)
            SsoUserInfo userInfoWithOrgId = new SsoUserInfo(
                mockUserInfo.getSubject(),
                mockUserInfo.getEmail(),
                mockUserInfo.getName(),
                mockUserInfo.getProtocol(),
                orgId  // Use the orgId from the actual call
            );
            return Uni.createFrom().item(userInfoWithOrgId);
        }

        // Default: return failure if no mock behavior configured
        return Uni.createFrom().failure(
            new IllegalStateException("MockSsoAdapter: No mock behavior configured")
        );
    }
}
