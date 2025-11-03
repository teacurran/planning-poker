# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I7.T4",
  "iteration_id": "I7",
  "iteration_goal": "Implement enterprise-tier features including SSO integration (OIDC/SAML2), organization management, member administration, org-level branding, and audit logging.",
  "description": "Extend `AuthController` to handle SSO authentication. New endpoint: `POST /api/v1/auth/sso/callback` (handle OIDC/SAML2 callback, validate assertion, extract user info, find or create user with JIT provisioning, assign to organization based on email domain, generate JWT tokens, return TokenPair). Integrate `SsoAdapter`, `OrganizationService`. JIT provisioning: if user doesn't exist, create User entity with SSO provider info, auto-add to organization if email domain matches. Domain-based org assignment (user with `@company.com` joins org with domain `company.com`). Log SSO login to AuditLog.",
  "agent_type_hint": "BackendAgent",
  "inputs": "SSO callback flow requirements, SsoAdapter from I7.T1, OrganizationService from I7.T2, AuditLogService from I7.T3",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java",
    "backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java",
    "backend/src/main/java/com/scrumpoker/api/rest/AuthController.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/AuthController.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/SsoCallbackRequest.java"
  ],
  "deliverables": "SSO callback endpoint handling OIDC and SAML2, User JIT provisioning (create user on first SSO login), Email domain matching for org assignment, OrgMember creation with MEMBER role on JIT provisioning, JWT token generation for SSO-authenticated user, Audit log entry for SSO login event",
  "acceptance_criteria": "OIDC callback creates user on first login, User auto-assigned to organization matching email domain, SAML2 callback works similarly, JWT tokens returned with org membership in claims, Existing user login skips provisioning, returns tokens, Audit log records SSO login with user ID and org ID",
  "dependencies": [
    "I7.T1",
    "I7.T2",
    "I7.T3"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents.

### Context: Enterprise SSO Authentication (from 05_Operational_Architecture.md)

**Enterprise SSO (Enterprise Tier):**
- **Protocols:** OIDC (OpenID Connect) and SAML2 support via Quarkus Security extensions
- **Configuration:** Per-organization SSO settings stored in `Organization.sso_config` JSONB field (IdP endpoint, certificate, attribute mapping)
- **Domain Enforcement:** Email domain verification ensures users with `@company.com` email automatically join organization workspace
- **Just-In-Time (JIT) Provisioning:** User accounts created on first SSO login with organization membership pre-assigned
- **Session Management:** SSO sessions synchronized with IdP via backchannel logout or session validation

### Context: RBAC Authorization Strategy

**Role-Based Access Control (RBAC):**
- **Roles:** `ANONYMOUS`, `USER`, `PRO_USER`, `ORG_ADMIN`, `ORG_MEMBER`
- **JWT Claims:** Access token includes `roles` array for authorization decisions
- **Dynamic Role Mapping:** Subscription tier mapped to roles during token generation

**Critical:** JWT tokens for SSO users MUST include organization membership in roles array.

---

## 3. Codebase Analysis & Strategic Guidance

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Summary:** Contains OAuth2 callback endpoint (`/api/v1/auth/oauth/callback`). Pattern: validate input → exchange code → find/create user → generate tokens → return response.
    *   **Recommendation:** ADD NEW endpoint `POST /api/v1/auth/sso/callback`. Follow EXACT SAME reactive pattern but substitute:
        - `OAuth2Adapter` → `SsoAdapter`
        - Add organization lookup by domain BEFORE user provisioning
        - Add org member creation after user provisioning
        - Call `AuditLogService.logSsoLogin()` after success
    *   **Critical Pattern:** Uses reactive types (`Uni<Response>`), chains with `.flatMap()` and `.map()`. MUST follow same pattern.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java`
    *   **Summary:** Provides `authenticate()` method for OIDC/SAML2. Returns `Uni<SsoUserInfo>` with user email, name, subject, protocol, organizationId, groups.
    *   **Recommendation:** MUST call with organization's SSO config. Requires: ssoConfigJson, authenticationData, SsoAuthParams (codeVerifier+redirectUri for OIDC), organizationId.
    *   **Critical:** Organization must be looked up FIRST by email domain before calling adapter.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
    *   **Summary:** Provides `addMember(orgId, userId, OrgRole)` to link user to organization.
    *   **Recommendation:** MUST call after JIT user provisioning to auto-assign user.
    *   **Critical:** Prevents duplicate memberships. Throws `IllegalStateException` if already member. Handle gracefully.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/AuditLogService.java`
    *   **Summary:** Provides `logSsoLogin(orgId, userId, ipAddress, userAgent)` for audit trail.
    *   **Recommendation:** MUST call after successful SSO auth. Fire-and-forget async.
    *   **Critical:** Requires IP address and user agent. Extract from HTTP request using `@Context HttpServletRequest`. Use `AuditLogService.extractIpAddress()` utility.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/OrganizationRepository.java`
    *   **Summary:** Provides `findByDomain(String domain)` to lookup org by email domain.
    *   **Recommendation:** MUST use to find organization. Extract domain from user email.
    *   **Critical:** If no org matches, return 401 Unauthorized: "No organization found for email domain: {domain}".

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoUserInfo.java`
    *   **Summary:** DTO with user info from SSO. Has `getEmailDomain()` method.
    *   **Recommendation:** SHOULD use to extract domain and verify it matches organization domain (security validation).

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/OAuthCallbackRequest.java`
    *   **Summary:** Request DTO for OAuth callback.
    *   **Recommendation:** CREATE NEW DTO `SsoCallbackRequest.java` with fields: `code`, `protocol`, `redirectUri`, `codeVerifier`. Use Bean Validation.

### Implementation Tips & Notes

*   **Critical Ordering:**
    1. Extract email domain from request
    2. Look up organization by domain (`organizationRepository.findByDomain()`)
    3. If no org found, return 401
    4. Call `SsoAdapter.authenticate()` with org's SSO config
    5. Find/create user with `UserService.findOrCreateUser()` (use "sso_{protocol}" as provider)
    6. Check if user is org member
    7. If not, call `OrganizationService.addMember()` with `OrgRole.MEMBER`
    8. Generate JWT tokens
    9. Call `AuditLogService.logSsoLogin()`
    10. Return TokenResponse

*   **SSO User Identification:** Use `oauthProvider` = "sso_{protocol}" (e.g., "sso_oidc"), `oauthSubject` = SSO subject from SsoUserInfo. This distinguishes SSO users from OAuth users.

*   **Reactive Chaining:** Chain operations:
    ```
    organizationRepository.findByDomain() → Uni<Organization>
      .flatMap(org → ssoAdapter.authenticate(...))
      .flatMap(ssoUserInfo → userService.findOrCreateUser(...))
      .flatMap(user → organizationService.addMember(...))
      .flatMap(member → jwtTokenService.generateTokens(...))
      .map(tokens → buildResponse(...))
    ```

*   **HTTP Context Injection:** For audit logging:
    ```java
    public Uni<Response> ssoCallback(@Valid SsoCallbackRequest request,
                                      @Context HttpServletRequest httpRequest) {
        String ipAddress = AuditLogService.extractIpAddress(
            httpRequest.getHeader("X-Forwarded-For"),
            httpRequest.getHeader("X-Real-IP"),
            httpRequest.getRemoteAddr()
        );
        String userAgent = httpRequest.getHeader("User-Agent");
    }
    ```

*   **Security Validation:** MUST verify email domain matches org domain:
    ```java
    String userDomain = ssoUserInfo.getEmailDomain();
    if (!userDomain.equalsIgnoreCase(organization.domain)) {
        return unauthorized("DOMAIN_MISMATCH", "Email domain mismatch");
    }
    ```

*   **Error Handling:** Follow existing pattern:
    - Validate inputs with detailed error messages
    - Use `.onFailure().recoverWithItem()` for reactive errors
    - Return appropriate HTTP status codes (400/401/500)
    - Log all errors with context

*   **SsoAuthParams Construction:** For OIDC, populate codeVerifier + redirectUri. For SAML2, these can be null. Check protocol from request.

---

## Summary of Actions Required

1. **CREATE** `SsoCallbackRequest.java` DTO with validation
2. **ADD** `POST /api/v1/auth/sso/callback` endpoint to AuthController
3. **IMPLEMENT** org lookup by domain as first step
4. **INTEGRATE** SsoAdapter, OrganizationService, AuditLogService
5. **VERIFY** JWT tokens include org membership claims
6. **TEST** against all acceptance criteria

---

**End of Task Briefing Package**
