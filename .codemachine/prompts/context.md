# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I5.T4",
  "iteration_id": "I5",
  "iteration_goal": "Implement Stripe subscription billing, tier enforcement (Free/Pro/Pro+/Enterprise), payment flows, webhook handling for subscription lifecycle events, and frontend upgrade UI.",
  "description": "Create `FeatureGate` service enforcing tier-based feature access. Methods: `canCreateInviteOnlyRoom(User)` (Pro+ or Enterprise), `canAccessAdvancedReports(User)` (Pro or higher), `canRemoveAds(User)` (Pro or higher), `canManageOrganization(User)` (Enterprise only). Inject into REST controllers and services. Throw `FeatureNotAvailableException` when user attempts unavailable feature. Implement `@RequiresTier(SubscriptionTier.PRO)` custom annotation for declarative enforcement on REST endpoints. Create interceptor validating tier requirements.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Feature tier matrix from product spec (Free vs. Pro vs. Enterprise), Subscription tier enum from I5.T2",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/user/SubscriptionTier.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/security/FeatureGate.java",
    "backend/src/main/java/com/scrumpoker/security/RequiresTier.java",
    "backend/src/main/java/com/scrumpoker/security/TierEnforcementInterceptor.java",
    "backend/src/main/java/com/scrumpoker/security/FeatureNotAvailableException.java"
  ],
  "deliverables": "FeatureGate service with tier check methods, Custom annotation @RequiresTier for declarative enforcement, Interceptor validating tier on annotated endpoints, FeatureNotAvailableException (403 Forbidden + upgrade prompt in message), Integration in RoomService (check tier before creating invite-only room)",
  "acceptance_criteria": "Free tier user cannot create invite-only room (403 error), Pro tier user can create invite-only room, Free tier user accessing advanced reports returns 403, Interceptor enforces @RequiresTier annotation on endpoints, Exception message includes upgrade CTA (e.g., \"Upgrade to Pro to access this feature\")",
  "dependencies": [
    "I5.T2"
  ],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Monetization Requirements (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: monetization-requirements -->
#### Monetization Requirements
- **Stripe Integration:** Subscription management, payment processing, webhook handling
- **Tier Enforcement:** Feature gating based on subscription level (ads, reports, room privacy, branding)
- **Upgrade Flows:** In-app prompts, modal CTAs, settings panel upsells
- **Billing Dashboard:** Subscription status, payment history, plan management
```

### Context: Reporting Requirements (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: reporting-requirements -->
#### Reporting Requirements
- **Free Tier:** Basic session summaries (story count, consensus rate, average vote)
- **Pro Tier:** Round-level detail, user consistency metrics, CSV/JSON/PDF export
- **Enterprise Tier:** Organizational dashboards, team trends, SSO-filtered reports, audit logs
```

### Context: Authorization Strategy (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: authorization-strategy -->
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
3. **Domain Services:** Business logic validates resource ownership and permissions
4. **Database:** Row-level security policies (future enhancement for multi-tenancy)
```

### Context: Task I5.T4 - Tier Enforcement Details (from 02_Iteration_I5.md)

```markdown
<!-- anchor: task-i5-t4 -->
*   **Task 5.4: Implement Subscription Tier Enforcement**
    *   **Task ID:** `I5.T4`
    *   **Description:** Create `FeatureGate` service enforcing tier-based feature access. Methods: `canCreateInviteOnlyRoom(User)` (Pro+ or Enterprise), `canAccessAdvancedReports(User)` (Pro or higher), `canRemoveAds(User)` (Pro or higher), `canManageOrganization(User)` (Enterprise only). Inject into REST controllers and services. Throw `FeatureNotAvailableException` when user attempts unavailable feature. Implement `@RequiresTier(SubscriptionTier.PRO)` custom annotation for declarative enforcement on REST endpoints. Create interceptor validating tier requirements.
    *   **Deliverables:**
        *   FeatureGate service with tier check methods
        *   Custom annotation @RequiresTier for declarative enforcement
        *   Interceptor validating tier on annotated endpoints
        *   FeatureNotAvailableException (403 Forbidden + upgrade prompt in message)
        *   Integration in RoomService (check tier before creating invite-only room)
    *   **Acceptance Criteria:**
        *   Free tier user cannot create invite-only room (403 error)
        *   Pro tier user can create invite-only room
        *   Free tier user accessing advanced reports returns 403
        *   Interceptor enforces @RequiresTier annotation on endpoints
        *   Exception message includes upgrade CTA (e.g., "Upgrade to Pro to access this feature")
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/SubscriptionTier.java`
    *   **Summary:** Enum defining the four subscription tiers: FREE, PRO, PRO_PLUS, ENTERPRISE. This matches the database `subscription_tier_enum` type.
    *   **Recommendation:** You MUST import and use this enum in your FeatureGate service for tier comparisons. The tier hierarchy is: FREE < PRO < PRO_PLUS < ENTERPRISE.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** User entity with a `subscriptionTier` field (type: `SubscriptionTier`, default: `FREE`). This field is automatically updated by BillingService when subscriptions change.
    *   **Recommendation:** Your FeatureGate service MUST accept a `User` object as a parameter and check the `user.subscriptionTier` field. DO NOT attempt to query subscriptions directly - the tier is always available on the User entity.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
    *   **Summary:** Service managing subscription lifecycle. Contains the `updateUserTier()` private method that ensures User.subscriptionTier is always in sync with active subscriptions. Also includes `isValidUpgrade()` helper method showing the tier hierarchy.
    *   **Recommendation:** You do NOT need to interact with BillingService. Your FeatureGate should ONLY read the User.subscriptionTier field, which BillingService keeps updated.

*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtAuthenticationFilter.java`
    *   **Summary:** Existing JAX-RS request filter that performs JWT authentication. Registered with `@Provider` and `@Priority(AUTHENTICATION)`. Populates SecurityIdentity with user principal and roles.
    *   **Recommendation:** Your TierEnforcementInterceptor MUST use a DIFFERENT priority than AUTHENTICATION. Use `@Priority(Priorities.AUTHORIZATION)` since tier enforcement is an authorization concern, not authentication. This ensures it runs AFTER authentication.

*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtClaims.java`
    *   **Summary:** Record containing JWT claims including `userId`, `email`, `roles`, and `tier` (as String). The SecurityIdentity contains JwtClaims in its attributes.
    *   **Recommendation:** In your interceptor, you can extract the tier from SecurityIdentity attributes if needed, but it's cleaner to fetch the User entity from the repository using the userId from the SecurityIdentity principal.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/PrivacyMode.java`
    *   **Summary:** Enum defining room privacy modes: PUBLIC, INVITE_ONLY, ORG_RESTRICTED.
    *   **Recommendation:** You MUST check for `PrivacyMode.INVITE_ONLY` when enforcing the invite-only room creation restriction in your FeatureGate.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** Service for room CRUD operations. The `createRoom()` method accepts a `PrivacyMode` parameter. Currently has NO tier enforcement.
    *   **Recommendation:** You MUST modify the `createRoom()` method to inject FeatureGate and call `featureGate.canCreateInviteOnlyRoom(owner)` before allowing INVITE_ONLY or ORG_RESTRICTED room creation. If the check fails, throw FeatureNotAvailableException.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserNotFoundException.java`
    *   **Summary:** Example of a domain exception pattern. Extends RuntimeException, includes the problematic entity ID, and has multiple constructors.
    *   **Recommendation:** You SHOULD follow this exact pattern for your FeatureNotAvailableException. Include fields for `requiredTier` and `currentTier` to provide context in the error message.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/exception/UserNotFoundExceptionMapper.java`
    *   **Summary:** JAX-RS exception mapper converting UserNotFoundException to 404 responses with ErrorResponse DTO. Uses `@Provider` annotation for automatic registration.
    *   **Recommendation:** You MUST create a similar exception mapper for FeatureNotAvailableException that returns HTTP 403 (Forbidden) status with an ErrorResponse containing an upgrade CTA message.

### Implementation Tips & Notes

*   **Tip:** The FeatureGate service should be a stateless `@ApplicationScoped` CDI bean for efficient dependency injection across the application.

*   **Tip:** For the `@RequiresTier` annotation, use `@Target({ElementType.METHOD, ElementType.TYPE})` so it can be applied to both individual endpoints and entire controller classes.

*   **Tip:** The TierEnforcementInterceptor should implement `ContainerRequestFilter` (JAX-RS) to intercept HTTP requests. Use reflection to check if the target method/class has the `@RequiresTier` annotation.

*   **Tip:** For the upgrade CTA message in FeatureNotAvailableException, use a format like: "This feature requires {requiredTier} tier. Upgrade your subscription to access it." Make it user-friendly and actionable.

*   **Tip:** When checking tier requirements, remember the hierarchy: PRO_PLUS satisfies PRO requirements, and ENTERPRISE satisfies both PRO and PRO_PLUS requirements. You'll need a helper method to check "is tier X sufficient for requirement Y?"

*   **Warning:** Be careful with the PrivacyMode check in RoomService. You should only enforce tier restrictions for INVITE_ONLY and ORG_RESTRICTED modes. PUBLIC rooms should remain accessible to all tiers (FREE users can create PUBLIC rooms).

*   **Warning:** The interceptor must NOT intercept public endpoints (those handled by JwtAuthenticationFilter's exemption list: /api/v1/auth/*, /q/health/*, etc.). You can check the request path before applying tier enforcement.

*   **Note:** Based on the architecture, the tier enforcement points are:
    1. **INVITE_ONLY rooms:** Requires PRO_PLUS or ENTERPRISE
    2. **ORG_RESTRICTED rooms:** Requires ENTERPRISE (organization membership)
    3. **Advanced reports:** Requires PRO or higher (not FREE)
    4. **Ad removal:** Requires PRO or higher
    5. **Organization management:** Requires ENTERPRISE

*   **Note:** The existing codebase uses Quarkus reactive patterns (Uni<>, Multi<>) extensively. Your FeatureGate methods should be synchronous (return boolean or throw exception) since they're simple tier comparisons, not I/O operations.

*   **Note:** The project follows comprehensive Javadoc commenting standards (see existing files). You MUST include detailed class-level and method-level Javadoc for all new classes, explaining the purpose, parameters, return values, and thrown exceptions.

*   **Note:** The task description asks for RoomService integration. After creating FeatureGate, you should inject it into RoomService and add a tier check in the `createRoom()` method before allowing INVITE_ONLY or ORG_RESTRICTED privacy modes. The check should throw FeatureNotAvailableException if the user's tier is insufficient.
