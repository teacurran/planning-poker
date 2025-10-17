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
  "dependencies": ["I3.T2"],
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

*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Summary:** This service implements JWT token lifecycle management including generation, validation, and refresh. It contains the critical `validateAccessToken(String token)` method that returns `Uni<JwtClaims>`.
    *   **Recommendation:** You MUST import and use the `JwtTokenService.validateAccessToken()` method in your filter to validate incoming JWT tokens. This method handles signature verification, expiration checks, and claims extraction.
    *   **Key Method:** `validateAccessToken(String token)` (lines 178-215) returns `Uni<JwtClaims>` containing userId, email, roles, and tier. Use this to populate the security context.
    *   **Implementation Detail:** The method uses SmallRye JWT's DefaultJWTParser which automatically validates signature using RSA public key from application.properties config.

*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtClaims.java`
    *   **Summary:** This is a record class representing the extracted JWT claims with fields: userId (UUID), email (String), roles (List<String>), and tier (String).
    *   **Recommendation:** After validating the token, you'll receive a `JwtClaims` object. Use this to create your security context implementation.
    *   **Helper Methods:** The record includes `hasRole(String)` and `hasAnyRole(String...)` utility methods for role checking.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Summary:** This controller contains authentication endpoints annotated with `@PermitAll`. It shows the pattern for public endpoints that should NOT be intercepted by your authentication filter.
    *   **Recommendation:** Your filter MUST skip authentication for endpoints under `/api/v1/auth/*` path prefix. The AuthController has three endpoints: `/oauth/callback`, `/refresh`, `/logout` - all are public.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/UserController.java`
    *   **Summary:** This controller has endpoints annotated with `@RolesAllowed("USER")` (lines 80, 120, 156) but currently contains TODO comments indicating that authentication is not yet enforced. Your filter will enable this enforcement.
    *   **Recommendation:** After your filter is implemented, these `@RolesAllowed` annotations will start working. Ensure your security context provides the roles from JWT claims so Quarkus Security can enforce these annotations.
    *   **Pattern:** Notice lines 61, 100, 137, 176 have TODOs about verifying authentication. Your filter will resolve these by enforcing auth automatically.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** Similar to UserController, this has `@RolesAllowed("USER")` annotations (lines 112, 173, 198) with TODO comments. It also shows patterns for accessing user context (currently set to null with TODO on line 60).
    *   **Recommendation:** Your security context implementation should allow controllers to access the authenticated user's ID. Consider storing JwtClaims in a way that controllers can retrieve the current user ID.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Contains JWT configuration properties including:
        - `mp.jwt.verify.issuer` (line 56) - Token issuer to verify
        - `mp.jwt.verify.publickey.location` (line 61) - RSA public key for signature verification
        - `mp.jwt.token.expiration` (line 70) - Access token TTL (1 hour = 3600 seconds)
    *   **Recommendation:** You don't need to read these properties directly in the filter. The `JwtTokenService` already uses them. Just be aware they exist for context.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/ErrorResponse.java`
    *   **Summary:** Standardized error response DTO used across all controllers for consistent error formatting.
    *   **Recommendation:** When returning 401 Unauthorized from your filter, construct an ErrorResponse with error code "UNAUTHORIZED" or "INVALID_TOKEN" and an appropriate message.

### Implementation Tips & Notes

*   **Tip - JAX-RS Filter Priority:** Use `@Priority(Priorities.AUTHENTICATION)` (import from `jakarta.ws.rs.Priorities`) to ensure your filter runs early in the request processing chain, before method interceptors that check `@RolesAllowed`.

*   **Tip - Reactive Challenge:** Since `JwtTokenService.validateAccessToken()` returns `Uni<JwtClaims>`, you need to handle reactive programming properly. JAX-RS ContainerRequestFilter is synchronous by nature. You have two options:
    1. Block on the Uni using `.await().indefinitely()` (simpler, acceptable for authentication)
    2. Use async processing (more complex, not needed for MVP)

    **Recommendation:** Option 1 is acceptable here since authentication should be fast (<50ms) and needs to complete before request proceeds.

*   **Tip - Header Extraction:** Extract the `Authorization` header using `containerRequestContext.getHeaderString("Authorization")`. Check if it starts with `"Bearer "` (note the space) and extract the token substring using `header.substring(7)` (length of "Bearer ").

*   **Tip - Public Endpoints:** You MUST skip authentication for:
    *   All paths starting with `/api/v1/auth/` (OAuth callback, refresh, logout)
    *   OPTIONS requests (for CORS preflight) - check `containerRequestContext.getMethod()`
    *   Health check endpoints: `/q/health/*`
    *   Swagger UI endpoints: `/q/swagger-ui/*` and `/q/openapi`
    *   Metrics endpoint: `/q/metrics`
    *   The simplest approach is to check the request URI path before performing authentication.

*   **Tip - 401 Response Construction:** When authentication fails, use:
    ```java
    ErrorResponse error = new ErrorResponse("UNAUTHORIZED", "Missing or invalid authentication token");
    Response response = Response.status(Response.Status.UNAUTHORIZED)
        .entity(error)
        .build();
    containerRequestContext.abortWith(response);
    ```

*   **Tip - Security Context Integration:** Quarkus Security expects a `SecurityIdentity` to be set. You'll need to:
    1. Create a `SecurityIdentity` implementation wrapping `JwtClaims`
    2. Use `QuarkusSecurityIdentity.builder()` to construct it
    3. Set the principal (user email or userId as string)
    4. Add all roles from `JwtClaims.roles` list
    5. Store the SecurityIdentity in the Quarkus security context

    **Example Pattern:**
    ```java
    SecurityIdentity identity = QuarkusSecurityIdentity.builder()
        .setPrincipal(new QuarkusPrincipal(claims.userId().toString()))
        .addRoles(new HashSet<>(claims.roles()))
        .build();
    // Inject and use io.quarkus.security.identity.SecurityIdentity
    ```

*   **Note - Existing TODOs:** Multiple controllers (UserController lines 61, 100, 137, 176; RoomController lines 60, 125, 182, 212) have TODO comments about verifying authentication. Your filter implementation will resolve these TODOs automatically, enabling the security checks that are currently stubbed out.

*   **Warning - Reactive Blocking:** Be careful when calling `.await().indefinitely()` on the Uni. This blocks the current thread, which is acceptable for the short duration of JWT validation. However, avoid blocking for extended periods.

*   **Pattern Observation:** The codebase uses comprehensive Javadoc comments on all classes and public methods. You SHOULD follow this pattern in your filter implementation for consistency. See JwtTokenService (lines 22-61) for an excellent example.

*   **Pattern Observation:** Exception handling in this codebase logs errors at appropriate levels:
    - ERROR for unexpected exceptions
    - WARN for validation failures
    - INFO for successful security events
    - DEBUG for trace-level details
    Follow this pattern in your filter.

*   **Critical - Role-Based Authorization:** The `@RolesAllowed` annotations in controllers specify role names like "USER". Your security context MUST provide these exact role names from the JWT claims' `roles` field. The `JwtTokenService.mapTierToRoles()` method (private, lines 293-313) already maps subscription tiers to roles like:
    - FREE tier → ["USER"]
    - PRO/PRO_PLUS → ["USER", "PRO_USER"]
    - ENTERPRISE → ["USER", "PRO_USER", "ORG_MEMBER"]

*   **Security Note:** Never log full JWT tokens. Only log metadata (user ID, expiration time). This is critical for security. See JwtTokenService line 184 for an example of safe logging (only first 10 chars of token).

*   **Implementation Order Recommendation:**
    1. First create the basic filter structure with public endpoint exemption
    2. Then add Bearer token extraction
    3. Then integrate JwtTokenService validation
    4. Then create SecurityIdentity and set it in context
    5. Finally add comprehensive error handling and logging

### Expected Files Structure

You need to create two files:

1. **JwtAuthenticationFilter.java** - The main JAX-RS ContainerRequestFilter implementation
   - Package: `com.scrumpoker.security`
   - Annotate with `@Provider` to register as JAX-RS provider
   - Annotate with `@Priority(Priorities.AUTHENTICATION)`
   - Implement `ContainerRequestFilter` interface
   - Inject `JwtTokenService` with `@Inject`
   - In the `filter(ContainerRequestContext)` method:
     - Check if path is public (skip authentication if so)
     - Extract Bearer token from Authorization header
     - Validate token using JwtTokenService (handle reactive Uni)
     - Create SecurityIdentity with claims
     - Set security context
     - Return 401 on authentication failure (use `abortWith()`)

2. **Custom SecurityIdentity implementation** (optional but recommended)
   - You may not need a separate file if using `QuarkusSecurityIdentity.builder()`
   - If you do create one, it should wrap JwtClaims
   - Provide principal name (userId or email as string)
   - Provide roles from claims
   - Optionally implement methods to retrieve full JwtClaims for controllers

### Quarkus Security Integration Details

**Key Classes to Use:**
- `io.quarkus.security.identity.SecurityIdentity` - Interface for security context
- `io.quarkus.security.runtime.QuarkusSecurityIdentity` - Builder for SecurityIdentity
- `io.quarkus.security.identity.request.AuthenticationRequest` - For custom authentication
- `jakarta.ws.rs.container.ContainerRequestContext` - Request context in filter

**Setting Security Context:**
After creating SecurityIdentity, you need to set it in Quarkus's security context. The recommended approach:

```java
@Inject
SecurityIdentity currentIdentity;

// In filter method, after validation:
containerRequestContext.setProperty("quarkus.security.identity", identity);
```

Alternatively, you can inject and use `IdentityProviderManager` for more control.

### Testing Strategy

After implementation, the acceptance criteria require:
- Protected endpoints return 401 without token
- Protected endpoints work with valid token
- Expired tokens return 401
- Public endpoints accessible without token
- @RolesAllowed annotations enforced

You should test these manually first using curl or Postman:
```bash
# Test protected endpoint without token (should return 401)
curl -X GET http://localhost:8080/api/v1/users/123e4567-e89b-12d3-a456-426614174000

# Test public endpoint without token (should work)
curl -X POST http://localhost:8080/api/v1/auth/oauth/callback -d '{...}'

# Test protected endpoint with valid token (should work)
curl -X GET http://localhost:8080/api/v1/users/123e4567-e89b-12d3-a456-426614174000 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

The existing integration tests in `UserControllerTest` and `RoomControllerTest` will need to be updated to include authentication tokens. There's a test profile `NoSecurityTestProfile` that may need adjusting.

### Critical Implementation Notes

1. **Do NOT validate @PermitAll annotations** - Focus on path-based public endpoint detection. The @PermitAll annotation is a hint but checking request paths is more reliable.

2. **Bearer Token Format** - Ensure you handle the "Bearer " prefix correctly (with space). Common mistake is to forget the space or not trim the token.

3. **Error Response Content-Type** - When aborting with 401, ensure the response has `application/json` content type. Use `.type(MediaType.APPLICATION_JSON)` on the Response builder.

4. **CORS Preflight** - OPTIONS requests MUST be allowed through without authentication, otherwise CORS will break for browser clients.

5. **Health Checks** - Never block health check endpoints with authentication. Kubernetes liveness/readiness probes need unauthenticated access to `/q/health/*`.

### Final Checklist

Before marking the task complete, verify:
- [ ] Filter annotated with @Provider and @Priority(AUTHENTICATION)
- [ ] Bearer token extracted from Authorization header correctly
- [ ] Token validated using JwtTokenService.validateAccessToken()
- [ ] SecurityIdentity created with userId and roles from JWT claims
- [ ] Security context set in Quarkus (enables @RolesAllowed to work)
- [ ] Public endpoints exempted: /api/v1/auth/*, /q/*, OPTIONS requests
- [ ] 401 Unauthorized returned for missing/invalid/expired tokens
- [ ] 401 response includes ErrorResponse JSON body
- [ ] Proper logging (security events, no token values logged)
- [ ] Code compiles with `mvn clean compile`
- [ ] Follows existing code style (Javadoc, error handling patterns)
- [ ] Manual testing confirms protected endpoints require valid JWT
- [ ] Manual testing confirms public endpoints work without JWT
- [ ] Manual testing confirms @RolesAllowed annotations are enforced
