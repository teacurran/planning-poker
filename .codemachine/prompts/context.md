# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T4",
  "iteration_id": "I3",
  "iteration_goal": "Implement OAuth2 authentication (Google, Microsoft), JWT token generation/validation, user registration/login flows, and frontend authentication UI to enable secured access to the application.",
  "description": "Create JAX-RS request filter (`@Provider`) for JWT authentication. Intercept requests to protected endpoints, extract JWT from `Authorization: Bearer <token>` header, validate token using `JwtTokenService`, extract user claims, set security context (user ID, roles) for authorization checks. Skip authentication for public endpoints (/api/v1/auth/*, OPTIONS requests). Handle authentication failures with 401 Unauthorized response. Integrate with Quarkus Security for `@RolesAllowed` annotations.",
  "agent_type_hint": "BackendAgent",
  "inputs": "JWT validation logic from I3.T2, JAX-RS filter patterns, Quarkus Security integration",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/security/JwtTokenService.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/security/JwtAuthenticationFilter.java",
    "backend/src/main/java/com/scrumpoker/security/SecurityContextImpl.java"
  ],
  "deliverables": "JwtAuthenticationFilter annotated with `@Provider` and `@Priority(AUTHENTICATION)`, Bearer token extraction from Authorization header, Token validation and claims extraction, Security context population (userId, roles, email), Public endpoint exemption (auth endpoints, health checks), 401 response for missing/invalid tokens",
  "acceptance_criteria": "Protected endpoints (e.g., GET /api/v1/users/{userId}) require valid JWT (401 if missing), Valid JWT allows request to proceed, populates security context, Expired JWT returns 401 Unauthorized, Public endpoints (/api/v1/auth/*) accessible without JWT, `@RolesAllowed` annotations work correctly (use roles from JWT claims)",
  "dependencies": [
    "I3.T2"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: authentication-mechanisms (from 05_Operational_Architecture.md)

```markdown
##### Authentication Mechanisms

**OAuth2 Social Login (Free/Pro Tiers):**
- **Providers:** Google OAuth2, Microsoft Identity Platform
- **Flow:** Authorization Code Flow with PKCE (Proof Key for Code Exchange) for browser-based clients
- **Implementation:** Quarkus OIDC extension handling token exchange and validation
- **Token Storage:** JWT access tokens (1-hour expiration) in browser `localStorage`, refresh tokens (30-day expiration) in `httpOnly` secure cookies
- **User Provisioning:** Automatic user creation on first login with `oauth_provider` and `oauth_subject` as unique identifiers
- **Profile Sync:** Email, display name, and avatar URL synced from OAuth provider on each login

**Enterprise SSO (Enterprise Tier):**
- **Protocols:** OIDC (OpenID Connect) and SAML2 support via Quarkus Security extensions
- **Configuration:** Per-organization SSO settings stored in `Organization.sso_config` JSONB field (IdP endpoint, certificate, attribute mapping)
- **Domain Enforcement:** Email domain verification ensures users with `@company.com` email automatically join organization workspace
- **Just-In-Time (JIT) Provisioning:** User accounts created on first SSO login with organization membership pre-assigned
- **Session Management:** SSO sessions synchronized with IdP via backchannel logout or session validation

**Anonymous Play:**
- **Identifier:** Client-generated UUID stored in browser `sessionStorage` for session continuity
- **Room Association:** Anonymous participants linked to room via `RoomParticipant.anonymous_id`
- **Feature Restrictions:** No session history access, no saved preferences, no administrative capabilities
- **Data Lifecycle:** Anonymous session data purged 24 hours after room inactivity
```

### Context: authorization-strategy (from 05_Operational_Architecture.md)

```markdown
##### Authorization Strategy

**Role-Based Access Control (RBAC):**
- **Roles:** `ANONYMOUS`, `USER`, `PRO_USER`, `ORG_ADMIN`, `ORG_MEMBER`
- **Implementation:** Quarkus Security annotations (`@RolesAllowed`) on REST endpoints and service methods
- **JWT Claims:** Access token includes `roles` array for authorization decisions
- **Dynamic Role Mapping:** Subscription tier (`FREE`, `PRO`, `PRO_PLUS`, `ENTERPRISE`) mapped to roles during token generation

**Resource-Level Permissions:**
- **Room Access:**
  - `PUBLIC` rooms: Accessible to anyone with room ID
  - `INVITE_ONLY` rooms: Requires room owner to whitelist participant (Pro+ tier)
  - `ORG_RESTRICTED` rooms: Requires organization membership (Enterprise tier)
- **Room Operations:**
  - Host controls (reveal, reset, kick): Room creator or user with `HOST` role in `RoomParticipant`
  - Configuration updates: Room owner only
  - Vote casting: Participants with `VOTER` role (excludes `OBSERVER`)
- **Report Access:**
  - Free tier: Session summary only (no round-level detail)
  - Pro tier: Full session history with round breakdown
  - Enterprise tier: Organization-wide analytics with member filtering

**Enforcement Points:**
1. **API Gateway/Ingress:** JWT validation and signature verification
2. **REST Controllers:** Role-based annotations reject unauthorized requests with `403 Forbidden`
3. **WebSocket Handshake:** Token validation before connection upgrade
4. **Service Layer:** Domain-level checks (e.g., room privacy mode enforcement, subscription feature gating)
```

### Context: application-security (from 05_Operational_Architecture.md)

```markdown
##### Application Security

**Input Validation:**
- **REST APIs:** Bean Validation (JSR-380) annotations on DTOs, automatic validation in Quarkus REST layer
- **WebSocket Messages:** Zod schema validation on client, server-side JSON schema validation before deserialization
- **SQL Injection Prevention:** Parameterized queries via Hibernate Reactive, no dynamic SQL concatenation
- **XSS Prevention:** React automatic escaping for user-generated content, CSP (Content Security Policy) headers

**Authentication Security:**
- **JWT Signature:** RS256 (RSA with SHA-256) algorithm, private key stored in Kubernetes Secret
- **Token Expiration:** Short-lived access tokens (1 hour), refresh tokens rotated on use
- **OAuth2 State Parameter:** CSRF protection for OAuth flow, state validated on callback
- **PKCE:** Protects authorization code from interception in browser-based flows

**Authorization Security:**
- **Least Privilege:** Default deny policy, explicit role grants required for resource access
- **Resource Ownership Validation:** Service layer verifies user owns/has permission for requested resource (e.g., room, report)
- **Rate Limiting:** Redis-backed token bucket algorithm:
  - Anonymous users: 10 req/min per IP
  - Authenticated users: 100 req/min per user
  - WebSocket messages: 50 msg/min per connection

**Data Protection:**
- **Encryption at Rest:** PostgreSQL Transparent Data Encryption (TDE) for sensitive columns (email, payment metadata)
- **PII Handling:** User emails hashed in logs, full values only in database and audit logs
- **Secrets Management:** Kubernetes Secrets for database credentials, OAuth client secrets, JWT signing keys
- **Payment Security:** Stripe tokenization for card details, no PCI-sensitive data stored in application database
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

**CRITICAL FINDING: The filter already exists!** The file `backend/src/main/java/com/scrumpoker/security/JwtAuthenticationFilter.java` is already fully implemented with comprehensive documentation and all required functionality.

*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtAuthenticationFilter.java`
    *   **Summary:** This file is a complete JAX-RS request filter implementing JWT authentication. It is annotated with `@Provider` and `@Priority(Priorities.AUTHENTICATION)`, intercepts all HTTP requests, extracts and validates JWT tokens from the Authorization header, populates the Quarkus security context, and handles public endpoints.
    *   **Current Implementation Status:**
        *   ✅ Bearer token extraction from `Authorization: Bearer <token>` header (lines 145-169)
        *   ✅ Token validation using `JwtTokenService.validateAccessToken()` (lines 178-179)
        *   ✅ Security context population with user principal and roles (lines 185-192)
        *   ✅ Public endpoint exemption for `/api/v1/auth/*`, `/q/health/*`, `/q/swagger-ui/*`, `/q/openapi`, `/q/metrics`, and OPTIONS requests (lines 232-259)
        *   ✅ 401 Unauthorized response for missing/invalid/expired tokens (lines 146-210, 272-283)
        *   ✅ Integration with Quarkus Security (setting `SECURITY_IDENTITY_KEY` in request context, line 192)
        *   ✅ JWT claims stored as security identity attribute for controllers to access (line 188)
    *   **Recommendation:** **DO NOT create a new file.** The filter is already complete and production-ready with excellent documentation. Review the existing implementation to verify it meets all acceptance criteria.

*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Summary:** This service provides JWT token generation, validation, and refresh functionality. It is the dependency for the authentication filter's validation logic.
    *   **Key Methods Used by Filter:**
        *   `validateAccessToken(String token)` - Returns `Uni<JwtClaims>` with userId, email, roles, tier (line 178)
        *   Automatically validates signature using RSA public key from config
        *   Checks token expiration and issuer claim
    *   **Recommendation:** The filter correctly uses this service's reactive API with blocking await (`.await().indefinitely()`) as documented in the filter's implementation notes (line 177-179).

*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtClaims.java`
    *   **Summary:** Record class representing JWT claims extracted from validated tokens. Contains userId (UUID), email (String), roles (List<String>), and tier (String).
    *   **Helper Methods:**
        *   `hasRole(String role)` - Check if user has specific role
        *   `hasAnyRole(String... roles)` - Check if user has any of specified roles
    *   **Recommendation:** The filter stores this complete JwtClaims object in the SecurityIdentity attributes (key: "jwt.claims") so controllers can access full claim data beyond just roles.

*   **File:** `backend/src/main/java/com/scrumpoker/security/SecurityContextImpl.java`
    *   **Current Status:** This file is mentioned in the target_files list but does NOT exist in the codebase.
    *   **Analysis:** The filter uses Quarkus's built-in `QuarkusSecurityIdentity` (line 185) instead of a custom implementation. This is the correct approach and aligns with Quarkus best practices.
    *   **Recommendation:** **DO NOT create SecurityContextImpl.java.** The task specification appears outdated - the implementation correctly uses `QuarkusSecurityIdentity.builder()` which is the standard Quarkus Security mechanism.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/UserController.java` and `RoomController.java`
    *   **Summary:** These controllers have `@RolesAllowed("USER")` annotations on protected endpoints (UserController lines 80, 120, 156; RoomController lines 112, 173, 198).
    *   **Current TODOs:** Multiple TODO comments indicate authentication is planned for "Iteration 3" (UserController lines 61, 100, 137, 175; RoomController lines 60, 125, 182, 212).
    *   **Recommendation:** With the authentication filter now active, these controllers will automatically enforce role-based access control. The TODO comments should be removed once you verify the filter is working.

*   **File:** `backend/src/test/java/com/scrumpoker/api/rest/NoSecurityTestProfile.java`
    *   **Summary:** Test profile that disables security for integration tests by enabling `TestSecurityIdentityAugmentor` which auto-grants USER role.
    *   **Recommendation:** This allows existing tests to continue passing while the filter is enabled. When writing tests specifically for authentication (in a future task), you'll need a different test profile that enables the filter.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Configuration file with JWT settings already in place.
    *   **Key JWT Properties:**
        *   `mp.jwt.verify.issuer` (line 56) - Token issuer validation
        *   `mp.jwt.verify.publickey.location` (line 61) - RSA public key path
        *   `mp.jwt.token.expiration` (line 70) - Access token TTL (3600 seconds / 1 hour)
        *   `quarkus.oidc.enabled=false` (line 84) - OIDC disabled, using custom filter
    *   **Recommendation:** No changes needed. All required configuration is present.

### Implementation Tips & Notes

*   **CRITICAL: Task is Already Complete!** The JwtAuthenticationFilter is fully implemented with all required functionality. Your task should focus on **verification and testing** rather than implementation.

*   **Verification Checklist:**
    1. Confirm the filter intercepts requests at AUTHENTICATION priority
    2. Verify Bearer token extraction handles missing/malformed headers correctly
    3. Test that public endpoints (`/api/v1/auth/*`, health checks, swagger) bypass authentication
    4. Validate that protected endpoints require JWT and return 401 when missing
    5. Ensure expired/invalid tokens return 401 Unauthorized
    6. Confirm SecurityIdentity is populated with correct userId and roles from token claims
    7. Verify `@RolesAllowed` annotations work correctly with roles from JWT

*   **Important Implementation Details Already Present:**
    *   The filter uses **blocking await** on the reactive `validateAccessToken()` call (line 179). This is documented as acceptable because JWT validation is fast (<50ms) and must complete before request proceeds.
    *   The filter **never logs full token values**, only metadata like first 10 characters (lines 171-172, 184) for security.
    *   Security context key uses the standard Quarkus constant `"quarkus.security.identity"` (line 110) for proper integration.
    *   JWT claims are stored in SecurityIdentity attributes under key `"jwt.claims"` (line 116) so controllers can access full claim data via `identity.getAttribute("jwt.claims")`.

*   **Public Endpoint Logic:** The `isPublicEndpoint()` method (lines 232-259) handles both with and without leading slash (e.g., `"api/v1/auth/"` and `"/api/v1/auth/"`) for robustness.

*   **Error Handling:** The filter provides specific error codes in ErrorResponse JSON:
    *   `MISSING_TOKEN` - No Authorization header
    *   `INVALID_TOKEN_FORMAT` - Header doesn't start with "Bearer "
    *   `EMPTY_TOKEN` - Bearer token is blank
    *   `INVALID_TOKEN` - Token validation failed (signature, expiration, etc.)

*   **Next Steps Recommendation:**
    *   Since the filter already exists, your primary task should be to **verify it works correctly** by:
        1. Running the application with `mvn quarkus:dev`
        2. Testing protected endpoints without JWT (should get 401)
        3. Testing with a valid JWT from the auth flow (should succeed)
        4. Testing public endpoints without JWT (should succeed)
        5. Optionally adding integration tests specifically for the filter's behavior
    *   The acceptance criteria can be met by demonstrating the existing implementation works correctly, not by rewriting it.

*   **WARNING:** The second target file `SecurityContextImpl.java` should NOT be created. The filter correctly uses Quarkus's built-in `QuarkusSecurityIdentity` which is the standard pattern. Creating a custom SecurityContext implementation would be unnecessary and potentially problematic.

### Code Quality Observations

*   **Excellent Documentation:** The existing filter has comprehensive JavaDoc explaining the authentication flow, public endpoints, security context integration, and implementation notes.
*   **Production-Ready Code:** Error handling is thorough, logging is security-aware (no token values logged), and the implementation follows Quarkus best practices.
*   **Test Coverage Gap:** While the NoSecurityTestProfile exists for integration tests, there are no dedicated tests for the authentication filter itself. Consider this for future work.
