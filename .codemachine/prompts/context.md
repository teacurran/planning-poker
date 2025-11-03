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

### Context: data-model-organization (from 03_System_Structure_and_Data.md)

```markdown
| **Organization** | Enterprise SSO workspace | `org_id` (PK), `name`, `domain`, `sso_config` (JSONB: OIDC/SAML2 settings), `branding` (JSONB), `subscription_id` (FK) |
| **OrgMember** | User-organization membership | `org_id` (FK), `user_id` (FK), `role` (ADMIN/MEMBER), `joined_at` |

**Design Principles:**
1. **Normalized Core Entities:** Users, Rooms, Organizations follow 3NF to prevent update anomalies
2. **Denormalized Read Models:** SessionSummary and VoteStatistics tables precompute aggregations for reporting performance
3. **JSONB for Flexibility:** RoomConfig, DeckDefinition, UserPreferences stored as JSONB to support customization without schema migrations
4. **Soft Deletes:** Critical entities (Users, Rooms) use `deleted_at` timestamp for audit trail and GDPR compliance
5. **Partitioning Strategy:** SessionHistory and AuditLog partitioned by month for query performance and data lifecycle management
```

### Context: task-i7-t1-plan (from 02_Iteration_I7.md)

```markdown
<!-- anchor: task-i7-t1 -->
*   **Task 7.1: Implement SSO Adapter (OIDC & SAML2)**
    *   **Task ID:** `I7.T1`
    *   **Description:** Create `SsoAdapter` service supporting OIDC and SAML2 protocols using Quarkus Security extensions. OIDC: configure IdP discovery endpoint, handle authorization code flow, validate ID token, extract user attributes (email, name, groups). SAML2: configure IdP metadata URL, handle SAML response, validate assertions, extract attributes from assertion. Map IdP attributes to User entity fields. Support per-organization SSO configuration (stored in Organization.sso_config JSONB). Implement backchannel logout (OIDC logout endpoint, SAML SLO).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   SSO requirements from architecture blueprint
        *   Quarkus OIDC and SAML2 extension documentation
        *   Enterprise SSO patterns (Okta, Azure AD)
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/05_Operational_Architecture.md` (SSO section)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java`
        *   `backend/src/main/java/com/scrumpoker/integration/sso/OidcProvider.java`
        *   `backend/src/main/java/com/scrumpoker/integration/sso/Saml2Provider.java`
        *   `backend/src/main/java/com/scrumpoker/integration/sso/SsoUserInfo.java` (DTO)
    *   **Deliverables:**
        *   SsoAdapter with OIDC and SAML2 support
        *   Organization-specific SSO configuration (IdP endpoint, certificate, attribute mapping from JSONB)
        *   User attribute extraction (email, name, groups/roles)
        *   Backchannel logout implementation
        *   SSO provider-specific implementations (Okta, Azure AD tested)
    *   **Acceptance Criteria:**
        *   OIDC authentication flow completes with test IdP (Okta sandbox)
        *   SAML2 authentication flow completes (Azure AD or SAML test IdP)
        *   User attributes correctly mapped from ID token/assertion
        *   Organization-specific SSO config loaded from database
        *   Logout endpoint invalidates SSO session
        *   Certificate validation works for SAML2
    *   **Dependencies:** []
    *   **Parallelizable:** Yes
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java`
    *   **Summary:** **CRITICAL - THIS FILE ALREADY EXISTS AND IS FULLY IMPLEMENTED!** The SsoAdapter service is already complete with OIDC and SAML2 support. It implements the Strategy pattern, delegating to OidcProvider and Saml2Provider based on the protocol in the SSO configuration. The adapter includes authentication, logout, and protocol support checking methods.
    *   **Recommendation:** **DO NOT REWRITE THIS FILE.** Review it carefully to understand the expected interface and integration pattern. The file shows that:
        - `authenticate()` method accepts ssoConfigJson (from Organization.ssoConfig), authenticationData, additionalParams, and organizationId
        - It parses the JSONB config into a `SsoConfig` POJO
        - It delegates to `OidcProvider` or `Saml2Provider` based on the protocol
        - It includes `logout()` method for backchannel logout/SLO
        - It uses reactive `Uni<>` return types consistent with the reactive stack
    *   **Key Integration Points:**
        - Expects `OidcProvider` and `Saml2Provider` to be injected via CDI `@Inject`
        - Expects `ObjectMapper` for JSON parsing
        - Uses custom `SsoAuthParams` and `SsoLogoutParams` inner classes for parameter passing
        - Throws `SsoAuthenticationException` on errors

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/OidcProvider.java`
    *   **Summary:** This file already exists and contains the OIDC provider implementation.
    *   **Recommendation:** Review this file to understand what it implements. You likely need to complete or enhance the OIDC implementation based on the task acceptance criteria.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/Saml2Provider.java`
    *   **Summary:** This file already exists and contains the SAML2 provider implementation.
    *   **Recommendation:** Review this file to understand what it implements. You likely need to complete or enhance the SAML2 implementation based on the task acceptance criteria.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoUserInfo.java`
    *   **Summary:** This file already exists and is the DTO for SSO user information.
    *   **Recommendation:** Review this file. It should contain fields like email, name, organizationId, and any group/role mappings returned from IdP.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoConfig.java`
    *   **Summary:** This file exists and is the POJO for deserializing Organization.ssoConfig JSONB.
    *   **Recommendation:** Verify it has nested `OidcConfig` and `Saml2Config` objects and a protocol field.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/OidcConfig.java`
    *   **Summary:** This file exists and contains OIDC configuration fields (IdP endpoint, client ID, etc.).
    *   **Recommendation:** Ensure it includes all fields needed: issuer/discoveryEndpoint, clientId, clientSecret, redirectUri.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/Saml2Config.java`
    *   **Summary:** This file exists and contains SAML2 configuration fields.
    *   **Recommendation:** Ensure it includes: IdP metadata URL, entity ID, certificate, attribute mappings.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/Organization.java`
    *   **Summary:** The Organization entity with `ssoConfig` JSONB field. This field stores the per-organization SSO configuration as a JSON string.
    *   **Recommendation:** The SsoAdapter already handles parsing this JSON string into the `SsoConfig` object. Ensure the JSONB schema matches what the SsoConfig POJOs expect.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/OrganizationRepository.java`
    *   **Summary:** Repository for Organization entities. Includes `findByDomain()` method for SSO domain verification.
    *   **Recommendation:** This repository will be used by the AuthController (in task I7.T4) to look up the organization by email domain, then retrieve the ssoConfig to pass to SsoAdapter.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/oauth/OAuth2Adapter.java`
    *   **Summary:** The existing OAuth2 adapter for Google and Microsoft social login (non-enterprise).
    *   **Recommendation:** Study this as a reference pattern. The SsoAdapter should follow similar patterns but:
        - Load config from database (Organization.ssoConfig) NOT from application.properties
        - Return `SsoUserInfo` (with organizationId) NOT `OAuthUserInfo`
        - Support per-org certificate validation for SAML2
        - Handle JIT provisioning with organization membership

### Implementation Tips & Notes

*   **Tip:** The task states "Create `SsoAdapter` service" but **THE FILE ALREADY EXISTS AND IS COMPLETE**. Based on my analysis, the SsoAdapter.java file at 480 lines is a fully implemented adapter with comprehensive Javadoc, error handling, and the Strategy pattern. Your task is NOT to rewrite it.

*   **Note:** The actual work for this task is to:
    1. **Review and potentially enhance the OidcProvider and Saml2Provider implementations** to ensure they meet all acceptance criteria
    2. **Verify the SsoUserInfo, SsoConfig, OidcConfig, Saml2Config DTOs** are complete
    3. **Test the integration** with actual IdPs (Okta sandbox for OIDC, Azure AD or test IdP for SAML2)
    4. **Ensure certificate validation works for SAML2** (this is explicitly called out in acceptance criteria)
    5. **Verify backchannel logout works** for both OIDC and SAML2

*   **Warning:** The acceptance criteria mention testing with "Okta sandbox" and "Azure AD or SAML test IdP". You will need to set up test configurations for these. The configuration should be stored in `application.properties` for test environments or use test-specific properties files.

*   **Tip:** The SsoAdapter uses reactive programming (`Uni<>` return types from Mutiny). Ensure the OidcProvider and Saml2Provider implementations also use reactive patterns to avoid blocking the reactive pipeline.

*   **Note:** For OIDC, you'll need to:
    - Exchange authorization code for tokens using the IdP's token endpoint
    - Validate the ID token signature (using JWKS from IdP)
    - Extract claims (sub, email, name, groups) from the ID token
    - Implement backchannel logout by calling the IdP's end_session_endpoint

*   **Note:** For SAML2, you'll need to:
    - Parse and decode the Base64-encoded SAML response
    - Validate the SAML assertion signature using the IdP's certificate from the config
    - Extract attributes from the assertion based on attribute mappings in the config
    - Implement Single Logout (SLO) by generating a SAML LogoutRequest

*   **Tip:** The architecture specifies that the SsoAdapter is organization-scoped (config from database) unlike OAuth2Adapter (config from application.properties). The SsoAdapter.java already handles this by accepting `ssoConfigJson` as a parameter and parsing it at runtime.

*   **Tip:** Error handling is critical for SSO. The existing SsoAdapter uses `SsoAuthenticationException` for all SSO-related errors. Ensure OidcProvider and Saml2Provider throw this exception with clear error messages for debugging.

*   **Note:** The task mentions "SSO provider-specific implementations (Okta, Azure AD tested)". This means you should ensure the OIDC implementation works with Okta's OIDC endpoints and the SAML2 implementation works with Azure AD's SAML configuration.

### File Structure Confirmation

Based on my codebase analysis, the following files already exist in `backend/src/main/java/com/scrumpoker/integration/sso/`:
- ✅ `SsoAdapter.java` (COMPLETE - 480 lines)
- ✅ `OidcProvider.java` (EXISTS - needs review/enhancement)
- ✅ `Saml2Provider.java` (EXISTS - needs review/enhancement)
- ✅ `SsoUserInfo.java` (EXISTS - DTO)
- ✅ `SsoConfig.java` (EXISTS - POJO)
- ✅ `OidcConfig.java` (EXISTS - POJO)
- ✅ `Saml2Config.java` (EXISTS - POJO)
- ✅ `SsoAuthenticationException.java` (EXISTS - custom exception)

**Your task is to review, test, and potentially enhance these files to meet all acceptance criteria, NOT to create them from scratch.**

### Testing Approach

1. **Unit Tests:** Test OidcProvider and Saml2Provider in isolation with mocked HTTP clients for IdP interactions
2. **Integration Tests:** Test the full SsoAdapter flow with test IdP configurations
3. **Manual Testing:** Test with real Okta sandbox and Azure AD configurations to verify the acceptance criteria

### Key Differences from OAuth2

The architecture document highlights these differences between SSO and OAuth2:
- **Organization-scoped:** Config loaded from `Organization.ssoConfig` database field, not `application.properties`
- **Returns SsoUserInfo:** Includes `organizationId` for JIT provisioning and org assignment
- **Per-org attribute mapping:** SAML2 attribute mappings configured per organization
- **Domain verification:** Used for JIT provisioning to assign users to correct organization
