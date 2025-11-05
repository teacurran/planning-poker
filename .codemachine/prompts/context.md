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

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: key-interaction-flow-oauth-login (from 04_Behavior_and_Communication.md)

```markdown
#### Key Interaction Flow: OAuth2 Authentication (Google/Microsoft)

##### Description

This sequence demonstrates the OAuth2 authorization code flow for user authentication via Google or Microsoft identity providers, JWT token generation, and session establishment.

##### Diagram (PlantUML)

~~~plantuml
@startuml

title OAuth2 Authentication Flow - Google/Microsoft Login

actor "User" as User
participant "SPA\n(React App)" as SPA
participant "Quarkus API\n(/api/v1/auth)" as API
participant "OAuth2 Adapter" as OAuth
participant "User Service" as UserService
participant "PostgreSQL" as DB
participant "Google/Microsoft\nOAuth2 Provider" as Provider

User -> SPA : Clicks "Sign in with Google"
activate SPA

SPA -> SPA : Generate PKCE code_verifier & code_challenge,\nstore in sessionStorage
SPA -> Provider : Redirect to authorization URL:\nhttps://accounts.google.com/o/oauth2/v2/auth\n?client_id=...&redirect_uri=...&code_challenge=...
deactivate SPA

User -> Provider : Grants permission
Provider -> SPA : Redirect to callback:\nhttps://app.scrumpoker.com/auth/callback?code=AUTH_CODE
activate SPA

SPA -> API : POST /api/v1/auth/oauth/callback\n{"provider":"google", "code":"AUTH_CODE", "codeVerifier":"..."}
deactivate SPA

activate API
API -> OAuth : exchangeCodeForToken(provider, code, codeVerifier)
activate OAuth

OAuth -> Provider : POST /token\n{code, client_id, client_secret, code_verifier}
Provider --> OAuth : {"access_token":"...", "id_token":"..."}

OAuth -> OAuth : Validate id_token signature (JWT),\nextract claims: {sub, email, name, picture}
OAuth --> API : OAuthUserInfo{subject, email, name, avatarUrl}
deactivate OAuth

API -> UserService : findOrCreateUser(provider="google", subject="...", email="...", name="...")
activate UserService

UserService -> DB : SELECT * FROM user WHERE oauth_provider='google' AND oauth_subject='...'
alt User exists
  DB --> UserService : User{user_id, email, subscription_tier, ...}
else New user
  DB --> UserService : NULL
  UserService -> DB : INSERT INTO user (oauth_provider, oauth_subject, email, display_name, avatar_url, subscription_tier)\nVALUES ('google', '...', '...', '...', '...', 'FREE')
  DB --> UserService : User{user_id, ...}
  UserService -> UserService : Create default UserPreference record
  UserService -> DB : INSERT INTO user_preference (user_id, default_deck_type, theme) VALUES (...)
end

UserService --> API : User{user_id, email, displayName, subscriptionTier}
deactivate UserService

API -> API : Generate JWT access token:\n{sub: user_id, email, tier, exp: now+1h}
API -> API : Generate refresh token (UUID),\nstore in Redis with 30-day TTL

API --> SPA : 200 OK\n{"accessToken":"...", "refreshToken":"...", "user":{...}}
deactivate API

activate SPA
SPA -> SPA : Store tokens in localStorage,\nstore user in Zustand state
SPA -> User : Redirect to Dashboard
deactivate SPA

@enduml
~~~
```

### Context: rest-api-endpoints (from 04_Behavior_and_Communication.md)

```markdown
#### REST API Endpoints Overview

**Authentication & User Management:**
- `POST /api/v1/auth/oauth/callback` - Exchange OAuth2 code for JWT tokens
- `POST /api/v1/auth/refresh` - Refresh expired access token
- `POST /api/v1/auth/logout` - Revoke refresh token
- `GET /api/v1/users/{userId}` - Retrieve user profile
- `PUT /api/v1/users/{userId}` - Update profile (display name, avatar)
- `GET /api/v1/users/{userId}/preferences` - Get user preferences
- `PUT /api/v1/users/{userId}/preferences` - Update default room settings, theme

**Organization Management (Enterprise):**
- `POST /api/v1/organizations` - Create organization workspace
- `GET /api/v1/organizations/{orgId}` - Get org settings
- `PUT /api/v1/organizations/{orgId}/sso` - Configure OIDC/SAML2 settings
- `POST /api/v1/organizations/{orgId}/members` - Invite member
- `DELETE /api/v1/organizations/{orgId}/members/{userId}` - Remove member
- `GET /api/v1/organizations/{orgId}/audit-logs` - Query audit trail
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Summary:** This is the main authentication controller handling OAuth2 authentication, token refresh, and logout. It already has the infrastructure for SSO callback at lines 347-560, which is ALREADY IMPLEMENTED.
    *   **Recommendation:** You MUST carefully examine lines 347-560 of this file. The `ssoCallback` method is ALREADY COMPLETE with all the required functionality including OIDC/SAML2 handling, JIT provisioning, organization assignment, JWT token generation, and audit logging.
    *   **CRITICAL OBSERVATION:** Task I7.T4 appears to be ALREADY DONE. The implementation includes:
        - SSO callback endpoint at `POST /api/v1/auth/sso/callback` (line 374)
        - Email domain extraction and organization lookup (lines 432-439)
        - SsoAdapter authentication integration (lines 464-469)
        - Domain verification (lines 475-481)
        - JIT user provisioning via UserService (lines 484-491)
        - Organization member auto-assignment (lines 497-509)
        - JWT token generation (lines 515-519)
        - Audit log event publishing (lines 521-527)

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java`
    *   **Summary:** This file provides the SSO adapter service that handles both OIDC and SAML2 protocols. It includes the `authenticate` method (lines 104-147) which is the main entry point for SSO authentication, delegating to protocol-specific providers.
    *   **Recommendation:** The AuthController MUST call `SsoAdapter.authenticate()` method, passing the organization's `ssoConfig` JSON string, the authentication code/response, the `SsoAuthParams` object (containing codeVerifier and redirectUri for OIDC), and the organization ID. This is ALREADY correctly implemented in AuthController lines 464-469.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
    *   **Summary:** This service handles organization management including member addition. The `addMember` method (lines 186-234) is crucial for auto-assigning SSO users to their organization.
    *   **Recommendation:** You MUST use `OrganizationService.addMember(orgId, userId, OrgRole.MEMBER)` to add the JIT-provisioned user to the organization. The method handles duplicate member checking and throws `IllegalStateException` if the user is already a member (line 216-220). The AuthController ALREADY handles this correctly by recovering from `IllegalStateException` at lines 503-509.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/AuditLogService.java`
    *   **Summary:** This service provides audit logging for enterprise compliance. The `logSsoLogin` convenience method (lines 272-278) is specifically designed for SSO login events.
    *   **Recommendation:** You MUST call `AuditLogService.logSsoLogin(orgId, userId, ipAddress, userAgent)` after successful SSO authentication. The AuthController ALREADY does this correctly at lines 522-527. Note that IP address extraction is handled by the static helper method `AuditLogService.extractIpAddress()` at lines 405-410 in AuthController.

### Implementation Tips & Notes

*   **Tip:** The DTO `SsoCallbackRequest` needs to include fields: `code` (String), `protocol` (String - "oidc" or "saml2"), `redirectUri` (String - for OIDC), `codeVerifier` (String - for OIDC PKCE), and `email` (String - for organization domain lookup). Based on AuthController usage at lines 395-428, these fields are ALREADY expected.

*   **Note:** The existing implementation in AuthController.ssoCallback() follows this exact flow:
    1. Extract email domain from request.email (line 432)
    2. Look up organization by domain using `organizationRepository.findByDomain()` (line 435)
    3. Validate organization has SSO configured (lines 445-449)
    4. Build `SsoAdapter.SsoAuthParams` for OIDC protocol (lines 452-461)
    5. Call `ssoAdapter.authenticate()` (lines 464-469)
    6. Verify email domain matches (lines 475-481)
    7. Call `userService.findOrCreateUser()` for JIT provisioning (lines 485-491)
    8. Call `organizationService.addMember()` with recovery for duplicate members (lines 497-509)
    9. Generate JWT tokens via `jwtTokenService.generateTokens()` (line 515)
    10. Log audit event via `auditLogService.logSsoLogin()` (lines 522-527)
    11. Return TokenResponse (line 532)

*   **Warning:** The task description asks you to "Extend AuthController to handle SSO authentication" but the code analysis shows this is ALREADY FULLY IMPLEMENTED. You should verify this implementation is complete and mark the task as done, OR if there's a specific aspect missing, identify it clearly. The acceptance criteria all appear to be met by the existing implementation.

*   **Critical:** When examining the code at AuthController lines 390-560, you'll see the complete SSO callback implementation with all deliverables:
    - ✅ SSO callback endpoint handling OIDC and SAML2
    - ✅ User JIT provisioning (create user on first SSO login)
    - ✅ Email domain matching for org assignment
    - ✅ OrgMember creation with MEMBER role on JIT provisioning
    - ✅ JWT token generation for SSO-authenticated user
    - ✅ Audit log entry for SSO login event

*   **Observation:** The `SsoCallbackRequest` DTO is already referenced in the AuthController import (line 6) and used as the parameter type (line 390). This DTO likely already exists and should be verified for completeness.

### Action Required

**VERIFY COMPLETION:** Before writing any code, you MUST:
1. Read the complete `SsoCallbackRequest` DTO to confirm all required fields exist
2. Verify the existing implementation against ALL acceptance criteria
3. If the implementation is complete, mark task I7.T4 as DONE
4. If any aspect is missing, document exactly what needs to be added
