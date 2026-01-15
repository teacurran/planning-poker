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
  "inputs": "*   JWT validation logic from I3.T2\n        *   JAX-RS filter patterns\n        *   Quarkus Security integration",
  "target_files": [
    "backend/src/main/java/com/scrumpoker/security/JwtAuthenticationFilter.java",
    "backend/src/main/java/com/scrumpoker/security/SecurityContextImpl.java"
  ],
  "input_files": [
    "backend/src/main/java/com/scrumpoker/security/JwtTokenService.java"
  ],
  "deliverables": "*   JwtAuthenticationFilter annotated with `@Provider` and `@Priority(AUTHENTICATION)`\n        *   Bearer token extraction from Authorization header\n        *   Token validation and claims extraction\n        *   Security context population (userId, roles, email)\n        *   Public endpoint exemption (auth endpoints, health checks)\n        *   401 response for missing/invalid tokens",
  "acceptance_criteria": "*   Protected endpoints (e.g., GET /api/v1/users/{userId}) require valid JWT (401 if missing)\n        *   Valid JWT allows request to proceed, populates security context\n        *   Expired JWT returns 401 Unauthorized\n        *   Public endpoints (/api/v1/auth/*) accessible without JWT\n        *   `@RolesAllowed` annotations work correctly (use roles from JWT claims)",
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

### Context: Task 3.4 – Implement JWT Authentication Filter (from .codemachine/artifacts/plan/02_Iteration_I3.md)

```markdown
*   **Task 3.4: Implement JWT Authentication Filter**
    *   **Task ID:** `I3.T4`
    *   **Description:** Create JAX-RS request filter (`@Provider`) for JWT authentication. Intercept requests to protected endpoints, extract JWT from `Authorization: Bearer <token>` header, validate token using `JwtTokenService`, extract user claims, set security context (user ID, roles) for authorization checks. Skip authentication for public endpoints (/api/v1/auth/*, OPTIONS requests). Handle authentication failures with 401 Unauthorized response. Integrate with Quarkus Security for `@RolesAllowed` annotations.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   JWT validation logic from I3.T2
        *   JAX-RS filter patterns
        *   Quarkus Security integration
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/security/JwtAuthenticationFilter.java`
        *   `backend/src/main/java/com/scrumpoker/security/SecurityContextImpl.java`
    *   **Deliverables:**
        *   JwtAuthenticationFilter annotated with `@Provider` and `@Priority(AUTHENTICATION)`
        *   Bearer token extraction from Authorization header
        *   Token validation and claims extraction
        *   Security context population (userId, roles, email)
        *   Public endpoint exemption (auth endpoints, health checks)
        *   401 response for missing/invalid tokens
    *   **Acceptance Criteria:**
        *   Protected endpoints (e.g., GET /api/v1/users/{userId}) require valid JWT (401 if missing)
        *   Valid JWT allows request to proceed, populates security context
        *   Expired JWT returns 401 Unauthorized
        *   Public endpoints (/api/v1/auth/*) accessible without JWT
        *   `@RolesAllowed` annotations work correctly (use roles from JWT claims)
    *   **Dependencies:** [I3.T2]
    *   **Parallelizable:** No (depends on JwtTokenService)
```

### Context: Authentication Mechanisms (from .codemachine/artifacts/architecture/05_Operational_Architecture.md)

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

### Context: Authorization Strategy (from .codemachine/artifacts/architecture/05_Operational_Architecture.md)

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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtAuthenticationFilter.java`
    *   **Summary:** Container request filter already wired with `@Provider` + `@Priority(Priorities.AUTHENTICATION)`. It skips traffic when `quarkus.security.auth.enabled=false`, checks `isPublicEndpoint`, validates `Authorization: Bearer` headers, calls `JwtTokenService.validateAccessToken(token).await().indefinitely()`, and builds a `SecurityIdentity` via `QuarkusSecurityIdentity.builder()` with the JWT roles plus a `jwt.claims` attribute. It aborts with a JSON `ErrorResponse` whenever token parsing fails.
    *   **Recommendation:** Extend functionality here (e.g., update public endpoint logic or logging) instead of creating new filters. Keep blocking validation localized, rely on `abortWithUnauthorized` helper for consistent payloads, and only set the security identity through `requestContext.setProperty("quarkus.security.identity", identity)` so Quarkus picks it up downstream.
*   **File:** `backend/src/main/java/com/scrumpoker/security/SecurityContextImpl.java`
    *   **Summary:** Application-scoped helper wrapping the injected `SecurityIdentity`. Provides accessors like `getCurrentUserId()`, `getCurrentUserEmail()`, `getCurrentUserTier()`, `getCurrentClaims()`, `hasRole()`, and `isCurrentUser(UUID)`. It expects the filter to stash a `JwtClaims` instance under the `jwt.claims` attribute and optionally honors a `scrumpoker.security.test-user-id` override for tests.
    *   **Recommendation:** Ensure whatever claims you attach inside the filter remain a `JwtClaims` object so these helpers continue working. If you adjust property keys, update both the filter and this service simultaneously. When adding new claims, prefer expanding `JwtClaims` rather than introducing ad-hoc attributes.
*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Summary:** Houses the validated JWT workflow—RS256 signing, Redis-backed refresh tokens, claim extraction, and error typing via `JwtException.Reason`. `validateAccessToken` wraps `JWTParser.parse()` and throws typed exceptions for expired vs invalid tokens, while helper methods (e.g., `mapTierToRoles`) determine role arrays.
    *   **Recommendation:** The filter should simply delegate to `validateAccessToken` and react to the exception reasons for precise logging/response text (already demonstrated). Avoid duplicating validation logic; any new error handling should rely on `JwtException.Reason` so the rest of the stack remains consistent.

### Implementation Tips & Notes
*   **Tip:** `isPublicEndpoint` currently allows POST/GET `/api/v1/rooms` for anonymous access plus Quarkus management paths. Update this list deliberately if business rules shift, and remember to handle both prefixed (`/api/...`) and non-prefixed variants to match current URI parsing.
*   **Tip:** Use the existing `ErrorResponse` DTO when aborting so frontend clients keep receiving `{ "code", "message" }` payloads. The helper already sets the `MediaType.APPLICATION_JSON` content type.
*   **Tip:** When authentication is disabled (tests), the filter returns immediately; keep that guard as the first operation so Quarkus test profiles can bypass JWT without stubbing headers.
*   **Tip:** The `SecurityIdentity` builder currently only sets principal + roles + claims; if future requirements need tier- or email-specific attributes, attach them here so `SecurityIdentity` consumers can inject `SecurityIdentity` directly rather than re-deriving them.
*   **Warning:** `SecurityContextImpl.getCurrentUserId()` throws if claims are missing. Always ensure downstream endpoints remain annotated with `@RolesAllowed` or other guards so they are not invoked without the filter populating claims; otherwise they may log noisy exceptions.
