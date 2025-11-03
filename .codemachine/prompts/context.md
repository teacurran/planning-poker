# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I7.T1",
  "iteration_id": "I7",
  "iteration_goal": "Implement enterprise-tier features including SSO integration (OIDC/SAML2), organization management, member administration, org-level branding, and audit logging.",
  "description": "Create `SsoAdapter` service supporting OIDC and SAML2 protocols using Quarkus Security extensions. OIDC: configure IdP discovery endpoint, handle authorization code flow, validate ID token, extract user attributes (email, name, groups). SAML2: configure IdP metadata URL, handle SAML response, validate assertions, extract attributes from assertion. Map IdP attributes to User entity fields. Support per-organization SSO configuration (stored in Organization.sso_config JSONB). Implement backchannel logout (OIDC logout endpoint, SAML SLO).",
  "agent_type_hint": "BackendAgent",
  "inputs": "SSO requirements from architecture blueprint, Quarkus OIDC and SAML2 extension documentation, Enterprise SSO patterns (Okta, Azure AD)",
  "input_files": [
    ".codemachine/artifacts/architecture/05_Operational_Architecture.md"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java",
    "backend/src/main/java/com/scrumpoker/integration/sso/OidcProvider.java",
    "backend/src/main/java/com/scrumpoker/integration/sso/Saml2Provider.java",
    "backend/src/main/java/com/scrumpoker/integration/sso/SsoUserInfo.java"
  ],
  "deliverables": "SsoAdapter with OIDC and SAML2 support, Organization-specific SSO configuration (IdP endpoint, certificate, attribute mapping from JSONB), User attribute extraction (email, name, groups/roles), Backchannel logout implementation, SSO provider-specific implementations (Okta, Azure AD tested)",
  "acceptance_criteria": "OIDC authentication flow completes with test IdP (Okta sandbox), SAML2 authentication flow completes (Azure AD or SAML test IdP), User attributes correctly mapped from ID token/assertion, Organization-specific SSO config loaded from database, Logout endpoint invalidates SSO session, Certificate validation works for SAML2",
  "dependencies": [],
  "parallelizable": true,
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

### Context: audit-logging (from 05_Operational_Architecture.md)

```markdown
**Audit Logging:**
- **Scope:** Enterprise tier security and compliance events
- **Storage:** Dedicated `AuditLog` table (partitioned by month) + immutable S3 bucket for archival
- **Events:**
  - User authentication (SSO login, logout)
  - Organization configuration changes (SSO settings, branding)
  - Member management (invite, role change, removal)
  - Administrative actions (room deletion, user account suspension)
- **Attributes:** `timestamp`, `orgId`, `userId`, `action`, `resourceType`, `resourceId`, `ipAddress`, `userAgent`, `changeDetails` (JSONB)
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

### Context: enterprise-requirements (from 01_Context_and_Drivers.md)

```markdown
#### Enterprise Requirements
- **SSO Integration:** OIDC and SAML2 support for identity providers (Okta, Azure AD, OneLogin)
- **Organization Management:** Multi-user workspaces with role-based access (Admin, Member)
- **Audit Logging:** Immutable event log for compliance (authentication, configuration changes, member management)
- **Custom Branding:** Organization logo, color scheme application-wide (Pro+ and Enterprise)
- **Advanced Analytics:** Organization-wide reporting dashboards with member filtering
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/integration/oauth/OAuth2Adapter.java`
    *   **Summary:** This is the existing OAuth2 integration adapter that handles Google and Microsoft social login. It uses a Strategy pattern with provider-specific implementations (`GoogleOAuthProvider`, `MicrosoftOAuthProvider`).
    *   **Recommendation:** You SHOULD follow the EXACT SAME PATTERN for SSO. Create `SsoAdapter` similar to `OAuth2Adapter`, with provider-specific implementations for OIDC and SAML2. Study the method signatures `exchangeCodeForToken()` and `validateIdToken()` as these patterns will be relevant for SSO token validation.
    *   **Key Insight:** The OAuth2Adapter demonstrates:
        - Provider routing via switch statement on provider name
        - Common interface (`OAuthUserInfo`) returned by all providers
        - Dependency injection of provider implementations
        - Comprehensive input validation before delegation
        - Structured logging with provider context

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/Organization.java`
    *   **Summary:** This entity already has the `ssoConfig` JSONB field (String type) and `branding` JSONB field for storing per-organization configuration.
    *   **Recommendation:** You MUST use the existing `Organization.ssoConfig` field to store IdP configuration. This field stores JSON as a String, so you'll need to serialize/deserialize SSO configuration POJOs (IdP endpoint, certificate, attribute mappings) to/from JSON. Consider creating a `SsoConfig` POJO similar to how other parts of the codebase handle JSONB.
    *   **Key Fields:**
        - `orgId` (UUID) - Primary key
        - `domain` (String) - Email domain for user auto-assignment (e.g., "acme.com")
        - `ssoConfig` (String, JSONB) - Where you'll store IdP settings
        - `subscription` (ManyToOne) - Link to enterprise subscription

*   **File:** `backend/src/main/java/com/scrumpoker/repository/OrganizationRepository.java`
    *   **Summary:** Repository with `findByDomain()` method that will be critical for SSO domain-based organization lookup.
    *   **Recommendation:** You MUST use `OrganizationRepository.findByDomain()` to look up organizations based on user email domain during SSO authentication. This enables automatic organization assignment (JIT provisioning flow).

*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Summary:** Service that generates JWT access tokens with user claims (userId, email, roles, tier) and manages refresh tokens in Redis.
    *   **Recommendation:** You WILL need to call this service after successful SSO authentication to generate JWT tokens. The SSO flow should: 1) Validate SSO assertion/token, 2) Extract user info, 3) Find or create user, 4) Call `JwtTokenService.generateTokens(user)` to get access/refresh token pair.
    *   **Key Methods:**
        - `generateTokens(User)` - Creates access and refresh tokens
        - Token includes roles array based on subscription tier
        - Access token: 1 hour expiration (RS256 signed)
        - Refresh token: 30 days in Redis

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Summary:** REST controller handling OAuth2 callback with JIT user provisioning. The `oauthCallback()` method demonstrates the complete flow: code exchange → user provisioning → token generation.
    *   **Recommendation:** You will eventually need to create an SSO callback endpoint similar to this OAuth2 callback. Study the flow in `oauthCallback()` method:
        1. Exchange authorization code for user info via adapter
        2. Call `UserService.findOrCreateUser(oauthUserInfo)` for JIT provisioning
        3. Generate tokens via `JwtTokenService.generateTokens(user)`
        4. Return TokenResponse with tokens and user profile
    *   **Note:** The SSO callback endpoint will be implemented in Task I7.T4, but understanding this pattern NOW is critical for designing the `SsoAdapter` return type and user attribute extraction.

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven dependencies configuration. Currently includes `quarkus-oidc` extension for OAuth2 support.
    *   **Recommendation:** You MUST add SAML2 support. Research and add the appropriate Quarkus SAML2 extension. Likely candidates:
        - `quarkus-elytron-security-saml` or similar WildFly Elytron SAML2 adapter
        - Consult Quarkus documentation for the correct SAML2 extension in Quarkus 3.15.1
        - DO NOT add external SAML libraries (like OpenSAML) if Quarkus provides a built-in extension
    *   **Current Extensions:** quarkus-oidc (line 88-90) is already present for OAuth2/OIDC support.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Application configuration with OIDC settings starting at line 78.
    *   **Recommendation:** You SHOULD add SSO-specific configuration properties for OIDC enterprise IdPs and SAML2 settings. Since SSO config is per-organization (stored in database), you may only need:
        - SAML2 entity ID for your application
        - ACS (Assertion Consumer Service) URL endpoint
        - Consider making these environment-configurable
    *   **Pattern:** Follow existing JWT config pattern (lines 54-75) with environment variable fallbacks like `${SSO_ENTITY_ID:default-value}`

### Implementation Tips & Notes

*   **Tip:** I found that the project consistently uses **Uni<>** reactive return types for all async operations. Your SsoAdapter methods MUST return reactive types (e.g., `Uni<SsoUserInfo>`), NOT blocking types.

*   **Tip:** JSONB fields in entities are stored as String type and require manual JSON serialization/deserialization. See how the codebase handles Room.config and Organization.ssoConfig. You SHOULD create helper POJOs (e.g., `SsoConfig.java`, `SamlConfig.java`, `OidcConfig.java`) and use Jackson ObjectMapper to serialize/deserialize to/from the String field.

*   **Note:** The OAuth2Adapter uses `@ApplicationScoped` CDI annotation and injects provider implementations. Your SsoAdapter MUST follow the same pattern with `@Inject` for `OidcProvider` and `Saml2Provider`.

*   **Warning:** Enterprise SSO is a NEW capability (Iteration 7). There are NO existing SSO-related files in the codebase. You're starting from scratch in the `integration/sso/` package. However, you MUST follow the established patterns from `integration/oauth/` package.

*   **Note:** The task mentions "backchannel logout" for OIDC and "SAML SLO" (Single Logout). This is ADVANCED functionality that may require:
    - OIDC: Implementing logout endpoint that receives backchannel logout tokens from IdP
    - SAML2: Handling LogoutRequest and LogoutResponse SAML messages
    - Consider marking backchannel logout as a "stretch goal" if time is limited - focus first on authentication flow

*   **Critical:** User attribute extraction must map IdP-specific claim names to our User entity fields:
    - OIDC: Standard claims (sub, email, name, picture, groups)
    - SAML2: Custom attribute mapping per organization (e.g., `emailAddress` attribute → email field)
    - The attribute mapping configuration SHOULD be stored in Organization.ssoConfig JSONB

*   **Architecture Insight:** The existing OAuth2 flow doesn't involve organization lookup because social login is for individual users. SSO is fundamentally DIFFERENT: it requires organization context. Your SsoAdapter MUST:
    1. Extract email domain from user attributes
    2. Look up organization by domain (use `OrganizationRepository.findByDomain()`)
    3. Load organization's SSO config from JSONB
    4. Use that config to validate the assertion/token
    This is a CIRCULAR dependency challenge: you need org config to validate, but you get user email from the validated token. SOLUTION: Validation happens in two phases - basic signature/structural validation first (using app-wide settings), then attribute extraction, then org-specific validation (if needed).

*   **Security Note:** SAML2 certificate validation is CRITICAL for security. The IdP's signing certificate MUST be validated against the certificate stored in Organization.ssoConfig. DO NOT skip certificate validation or use test/development modes that bypass validation in production code.

*   **Testing Strategy:** The acceptance criteria mention "Okta sandbox" and "Azure AD or SAML test IdP". You SHOULD:
    - Use Okta's free developer account for OIDC testing
    - Use SAMLtest.id or similar free SAML test IdP for SAML2 testing
    - Create integration tests with MOCKED IdP responses for CI/CD pipeline
    - DO NOT require real IdP credentials for automated tests

*   **Return Type Design:** Based on OAuth2Adapter pattern, create `SsoUserInfo` DTO similar to `OAuthUserInfo`. It should contain:
    - subject (unique IdP user identifier)
    - email
    - name (display name)
    - groups/roles (optional, for RBAC mapping)
    - provider ("oidc" or "saml2")
    - orgId (UUID) - This is DIFFERENT from OAuth2, SSO is organization-scoped

*   **Error Handling:** Follow the existing pattern in OAuth2Adapter - throw custom exceptions:
    - Create `SsoAuthenticationException` similar to `OAuth2AuthenticationException`
    - Throw for: invalid assertions, expired tokens, certificate validation failures, unsupported IdP configurations
    - Include detailed error messages for debugging but sanitize before returning to client (avoid leaking security details)

### Package Structure Recommendation

Create the following structure mirroring `integration/oauth/`:

```
backend/src/main/java/com/scrumpoker/integration/sso/
├── SsoAdapter.java              (main facade, similar to OAuth2Adapter)
├── SsoUserInfo.java             (DTO for extracted user attributes)
├── SsoAuthenticationException.java  (custom exception)
├── OidcProvider.java            (OIDC-specific implementation)
├── Saml2Provider.java           (SAML2-specific implementation)
├── SsoConfig.java               (POJO for deserializing Organization.ssoConfig JSONB)
└── package-info.java            (Javadoc package description)
```

### Quarkus Extensions Research Required

Before starting implementation, you MUST research:
1. Confirm the correct Quarkus SAML2 extension for version 3.15.1
2. Review Quarkus OIDC documentation for enterprise IdP configuration patterns
3. Understand how to configure multi-tenant OIDC (per-organization IdP URLs)
4. Check if Quarkus has built-in support for SAML2 or if you need WildFly Elytron integration

### JSON Schema for Organization.ssoConfig

Based on architecture requirements, the JSONB should support both OIDC and SAML2:

```json
{
  "protocol": "oidc",
  "oidc": {
    "issuer": "https://your-org.okta.com",
    "clientId": "client-id",
    "clientSecret": "encrypted-secret",
    "authorizationEndpoint": "...",
    "tokenEndpoint": "...",
    "userInfoEndpoint": "...",
    "jwksUri": "..."
  },
  "saml2": {
    "idpEntityId": "https://idp.example.com",
    "ssoUrl": "https://idp.example.com/sso",
    "sloUrl": "https://idp.example.com/slo",
    "certificate": "-----BEGIN CERTIFICATE-----\n...",
    "attributeMapping": {
      "email": "emailAddress",
      "name": "displayName",
      "groups": "memberOf"
    }
  },
  "domainVerificationRequired": true,
  "jitProvisioningEnabled": true
}
```

You SHOULD create POJOs matching this structure for type-safe configuration access.
