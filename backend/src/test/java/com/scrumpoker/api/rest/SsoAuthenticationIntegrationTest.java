package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.SsoCallbackRequest;
import com.scrumpoker.domain.organization.AuditLog;
import com.scrumpoker.domain.organization.Organization;
import com.scrumpoker.domain.organization.OrgMember;
import com.scrumpoker.domain.organization.OrgRole;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.integration.sso.SsoAdapter;
import com.scrumpoker.integration.sso.SsoUserInfo;
import com.scrumpoker.repository.AuditLogRepository;
import com.scrumpoker.repository.OrganizationRepository;
import com.scrumpoker.repository.OrgMemberRepository;
import com.scrumpoker.repository.UserRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for SSO authentication flow.
 * Tests end-to-end SSO callback handling: authentication → JIT provisioning →
 * organization assignment → JWT token generation → audit logging.
 *
 * <p>IMPORTANT: SAML2 support is planned but NOT YET IMPLEMENTED. Only OIDC
 * protocol tests are included. SAML2 tests will be added in future iteration.</p>
 *
 * <p>Uses @QuarkusTest with Testcontainers PostgreSQL for full integration testing.
 * Uses a test-scoped alternative SsoAdapter to avoid requiring actual IdP HTTP calls.</p>
 */
@QuarkusTest
@TestProfile(SsoAuthenticationIntegrationTest.SsoTestProfile.class)
public class SsoAuthenticationIntegrationTest {

    @Inject
    MockSsoAdapter mockSsoAdapter;

    @Inject
    OrganizationRepository organizationRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    OrgMemberRepository orgMemberRepository;

    @Inject
    AuditLogRepository auditLogRepository;

    private Organization testOrganization;
    private static final String TEST_DOMAIN = "acmecorp.com";
    private static final String TEST_USER_EMAIL = "john.doe@acmecorp.com";
    private static final String TEST_USER_NAME = "John Doe";
    private static final String TEST_SSO_SUBJECT = "oidc-subject-123456";

    /**
     * Test profile that extends NoSecurityTestProfile and enables MockSsoAdapter.
     */
    public static class SsoTestProfile extends NoSecurityTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            // Enable both TestSecurityIdentityAugmentor (from parent) and MockSsoAdapter
            Set<Class<?>> parentAlternatives = super.getEnabledAlternatives();
            Set<Class<?>> alternatives = new java.util.HashSet<>(parentAlternatives);
            alternatives.add(MockSsoAdapter.class);
            return alternatives;
        }
    }

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Reset mock to default state
        mockSsoAdapter.reset();

        // Clean up test data before each test
        asserter.execute(() -> Panache.withTransaction(() ->
            auditLogRepository.deleteAll()
                .flatMap(ignored -> orgMemberRepository.deleteAll())
                .flatMap(ignored -> userRepository.deleteAll())
                .flatMap(ignored -> organizationRepository.deleteAll())
        ));

        // Create test organization with SSO configuration
        asserter.execute(() -> Panache.withTransaction(() -> {
            testOrganization = new Organization();
            testOrganization.name = "Acme Corporation";
            testOrganization.domain = TEST_DOMAIN;
            testOrganization.ssoConfig = createOidcConfigJson();
            testOrganization.createdAt = Instant.now();
            testOrganization.updatedAt = Instant.now();
            return organizationRepository.persist(testOrganization);
        }));

        // Setup default mock behavior for successful authentication
        SsoUserInfo defaultUserInfo = new SsoUserInfo(
            TEST_SSO_SUBJECT,
            TEST_USER_EMAIL,
            TEST_USER_NAME,
            "oidc",
            null  // orgId will be set by the mock based on the actual call
        );
        mockSsoAdapter.configureMockSuccess(defaultUserInfo);
    }

    // ========================================
    // OIDC SSO Authentication Tests
    // ========================================

    @Test
    @RunOnVertxContext
    public void testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg(UniAsserter asserter) {
        // Given: MockSsoAdapter will return successful authentication (default behavior)

        // Create SSO callback request
        SsoCallbackRequest request = new SsoCallbackRequest();
        request.code = "mock-authorization-code";
        request.protocol = "oidc";
        request.redirectUri = "https://app.scrumpoker.com/auth/callback";
        request.codeVerifier = "mock-code-verifier-12345";
        request.email = TEST_USER_EMAIL;

        // When: Call SSO callback endpoint
        // Note: REST Assured HTTP calls should NOT be wrapped in asserter.execute()
        // because they are blocking calls that can deadlock when run on Vert.x event loop
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .header("X-Forwarded-For", "192.168.1.100")
            .header("User-Agent", "Mozilla/5.0 Test Browser")
        .when()
            .post("/api/v1/auth/sso/callback")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .body("user.email", equalTo(TEST_USER_EMAIL))
            .body("user.displayName", equalTo(TEST_USER_NAME))
            .body("user.subscriptionTier", equalTo("FREE"));

        // Then: Verify user was created via JIT provisioning
        asserter.assertThat(() -> Panache.withTransaction(() ->
            userRepository.findByOAuthProviderAndSubject("sso_oidc", TEST_SSO_SUBJECT)
        ), foundUser -> {
            User user = (User) foundUser;
            assertThat(user).isNotNull();
            assertThat(user.email).isEqualTo(TEST_USER_EMAIL);
            assertThat(user.displayName).isEqualTo(TEST_USER_NAME);
            assertThat(user.oauthProvider).isEqualTo("sso_oidc");
            assertThat(user.oauthSubject).isEqualTo(TEST_SSO_SUBJECT);
            assertThat(user.subscriptionTier).isEqualTo(SubscriptionTier.FREE);
        });

        // And: Verify user was assigned to organization
        asserter.assertThat(() -> Panache.withTransaction(() ->
            userRepository.findByOAuthProviderAndSubject("sso_oidc", TEST_SSO_SUBJECT)
                .flatMap(user -> orgMemberRepository.findByOrgIdAndUserId(testOrganization.orgId, user.userId))
        ), foundMember -> {
            OrgMember member = (OrgMember) foundMember;
            assertThat(member).isNotNull();
            assertThat(member.role).isEqualTo(OrgRole.MEMBER);
            assertThat(member.organization.orgId).isEqualTo(testOrganization.orgId);
        });

        // And: Verify audit log entry was created (with small delay for async processing)
        asserter.execute(() -> {
            try {
                Thread.sleep(500); // Give async audit logging time to complete
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        asserter.assertThat(() -> Panache.withTransaction(() ->
            auditLogRepository.listAll()
                .map(logs -> logs.stream()
                    .filter(log -> "SSO_LOGIN".equals(log.action))
                    .findFirst()
                    .orElse(null))
        ), foundLog -> {
            AuditLog auditLog = (AuditLog) foundLog;
            assertThat(auditLog).isNotNull();
            assertThat(auditLog.action).isEqualTo("SSO_LOGIN");
            assertThat(auditLog.resourceType).isEqualTo("USER");
            assertThat(auditLog.organization.orgId).isEqualTo(testOrganization.orgId);
            assertThat(auditLog.ipAddress).isEqualTo("192.168.1.100");
            assertThat(auditLog.userAgent).isEqualTo("Mozilla/5.0 Test Browser");
        });
    }

    @Test
    @RunOnVertxContext
    public void testOidcSsoCallback_ReturningUser_DoesNotDuplicateOrgMembership(UniAsserter asserter) {
        // Given: Create existing user and org membership
        User existingUser = new User();
        existingUser.email = TEST_USER_EMAIL;
        existingUser.displayName = TEST_USER_NAME;
        existingUser.oauthProvider = "sso_oidc";
        existingUser.oauthSubject = TEST_SSO_SUBJECT;
        existingUser.subscriptionTier = SubscriptionTier.FREE;

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(existingUser)
        ));

        // Create existing org membership
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.findById(existingUser.userId)
                .flatMap(user -> organizationRepository.findById(testOrganization.orgId)
                    .flatMap(org -> {
                        OrgMember existingMember = new OrgMember();
                        existingMember.id = new com.scrumpoker.domain.organization.OrgMemberId(org.orgId, user.userId);
                        existingMember.organization = org;
                        existingMember.user = user;
                        existingMember.role = OrgRole.MEMBER;
                        existingMember.joinedAt = Instant.now();
                        return orgMemberRepository.persist(existingMember);
                    }))
        ));

        // Given: MockSsoAdapter will return successful authentication (default behavior)

        // Create SSO callback request
        SsoCallbackRequest request = new SsoCallbackRequest();
        request.code = "mock-authorization-code-returning";
        request.protocol = "oidc";
        request.redirectUri = "https://app.scrumpoker.com/auth/callback";
        request.codeVerifier = "mock-code-verifier-67890";
        request.email = TEST_USER_EMAIL;

        // When: Call SSO callback endpoint (second login)
        // Note: REST Assured HTTP calls should NOT be wrapped in asserter.execute()
        // because they are blocking calls that can deadlock when run on Vert.x event loop
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .header("X-Forwarded-For", "10.0.0.50")
            .header("User-Agent", "Chrome Test")
        .when()
            .post("/api/v1/auth/sso/callback")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("user.email", equalTo(TEST_USER_EMAIL));

        // Then: Verify no duplicate org membership was created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            orgMemberRepository.listAll()
                .map(members -> members.stream()
                    .filter(m -> m.user.userId.equals(existingUser.userId))
                    .filter(m -> m.organization.orgId.equals(testOrganization.orgId))
                    .count())
        ), foundCount -> {
            Long count = (Long) foundCount;
            assertThat(count).isEqualTo(1L); // Still only 1 membership
        });
    }

    @Test
    public void testOidcSsoCallback_MissingEmail_Returns400() {
        // Given: Request without email
        SsoCallbackRequest request = new SsoCallbackRequest();
        request.code = "mock-authorization-code";
        request.protocol = "oidc";
        request.redirectUri = "https://app.scrumpoker.com/auth/callback";
        request.codeVerifier = "mock-code-verifier";
        request.email = null; // Missing email

        // When/Then: Should return 400 Bad Request
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/auth/sso/callback")
        .then()
            .statusCode(400)
            .body("error", notNullValue());
            // Note: Message may be generic validation error or specific "Email is required"
    }

    @Test
    public void testOidcSsoCallback_UnknownDomain_Returns401() {
        // Given: Email with unknown domain
        SsoCallbackRequest request = new SsoCallbackRequest();
        request.code = "mock-authorization-code";
        request.protocol = "oidc";
        request.redirectUri = "https://app.scrumpoker.com/auth/callback";
        request.codeVerifier = "mock-code-verifier";
        request.email = "user@unknowndomain.com"; // Domain not in database

        // When/Then: Should return 401 Unauthorized
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/auth/sso/callback")
        .then()
            .statusCode(401)
            .body("error", notNullValue())
            .body("message", containsString("No organization found"));
    }

    @Test
    public void testOidcSsoCallback_MissingCodeVerifier_Returns400() {
        // Given: OIDC request without code verifier
        SsoCallbackRequest request = new SsoCallbackRequest();
        request.code = "mock-authorization-code";
        request.protocol = "oidc";
        request.redirectUri = "https://app.scrumpoker.com/auth/callback";
        request.codeVerifier = null; // Missing code verifier (required for OIDC)
        request.email = TEST_USER_EMAIL;

        // When/Then: Should return 400 Bad Request
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/auth/sso/callback")
        .then()
            .statusCode(400)
            .body("error", notNullValue())
            .body("message", containsString("Code verifier is required"));
    }

    @Test
    public void testOidcSsoCallback_DomainMismatch_Returns401() {
        // Given: Override mock to return user with different domain (hacker@evil.com)
        SsoUserInfo mismatchUserInfo = new SsoUserInfo(
            "oidc-subject-mismatch",
            "hacker@evil.com",  // Different domain than organization
            "Hacker User",
            "oidc",
            null  // orgId will be set by the mock
        );
        mockSsoAdapter.configureMockSuccess(mismatchUserInfo);

        // Request with acmecorp.com email
        SsoCallbackRequest request = new SsoCallbackRequest();
        request.code = "mock-authorization-code";
        request.protocol = "oidc";
        request.redirectUri = "https://app.scrumpoker.com/auth/callback";
        request.codeVerifier = "mock-code-verifier";
        request.email = TEST_USER_EMAIL; // acmecorp.com

        // When/Then: Should return 401 for domain mismatch
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/auth/sso/callback")
        .then()
            .statusCode(401)
            .body("error", notNullValue())
            .body("message", containsString("domain does not match"));
    }

    // ========================================
    // TODO: SAML2 SSO Authentication Tests
    // ========================================

    // NOTE: SAML2 protocol is planned but NOT YET IMPLEMENTED in the codebase.
    // The SsoAdapter only supports OIDC protocol (see SsoAdapter lines 121-126).
    // SAML2 integration tests will be added in a future iteration when SAML2
    // support is implemented.

    // @Test
    // @RunOnVertxContext
    // public void testSaml2SsoCallback_FirstLogin_CreatesUserAndAssignsToOrg(UniAsserter asserter) {
    //     // TODO: Implement when SAML2 support is added to SsoAdapter
    // }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Creates a sample OIDC configuration JSON string for test organization.
     * Matches the format expected by SsoAdapter and SsoConfig.
     */
    private String createOidcConfigJson() {
        return """
                {
                    "protocol": "oidc",
                    "oidc": {
                        "issuer": "https://acmecorp.okta.com",
                        "clientId": "test-client-id",
                        "clientSecret": "test-client-secret"
                    }
                }
                """;
    }
}
