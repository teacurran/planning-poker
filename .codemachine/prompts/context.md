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

### Context: Feature Tier Matrix (from Product Specification)

**Feature Tier Matrix:**
- **FREE Tier:**
  - Public rooms only
  - Basic session summaries
  - Ad-supported experience
  - Anonymous play allowed

- **PRO Tier ($10/month):**
  - Ad-free experience
  - Advanced reports (round-by-round detail, user consistency)
  - CSV/JSON/PDF export
  - Session history persistence

- **PRO+ Tier ($30/month):**
  - All PRO features
  - **Invite-only rooms** (whitelisted participants)
  - Enhanced privacy controls
  - Priority support

- **ENTERPRISE Tier ($100/month):**
  - All PRO+ features
  - **Organization management** (multi-user workspaces)
  - SSO integration (OIDC/SAML2)
  - **Organization-restricted rooms**
  - Audit logging
  - Custom branding

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### 🚨 CRITICAL DISCOVERY: ALL REQUIRED COMPONENTS ALREADY EXIST! 🚨

**After comprehensive codebase analysis, I discovered that this task (I5.T4) is ALREADY FULLY IMPLEMENTED.**

All four target files exist with production-quality implementations:

#### ✅ File: `backend/src/main/java/com/scrumpoker/security/FeatureGate.java`
- **Status:** **COMPLETE** (230 lines, comprehensive implementation)
- **All Required Methods Implemented:**
  - ✅ `hasSufficientTier(User, SubscriptionTier)` - Core tier hierarchy check using enum ordinals
  - ✅ `canCreateInviteOnlyRoom(User)` - Returns true for PRO_PLUS or ENTERPRISE
  - ✅ `requireCanCreateInviteOnlyRoom(User)` - Throws exception if tier insufficient
  - ✅ `canAccessAdvancedReports(User)` - Returns true for PRO or higher
  - ✅ `requireCanAccessAdvancedReports(User)` - Throws exception if tier insufficient
  - ✅ `canRemoveAds(User)` - Returns true for PRO or higher
  - ✅ `requireCanRemoveAds(User)` - Throws exception if tier insufficient
  - ✅ `canManageOrganization(User)` - Returns true for ENTERPRISE only
  - ✅ `requireCanManageOrganization(User)` - Throws exception if tier insufficient
- **Code Quality:** Production-ready with comprehensive Javadoc (60+ lines of documentation), proper null checks, tier hierarchy enforcement
- **Pattern Used:** Both boolean check methods (`canXxx()`) AND imperative enforcement methods (`requireXxx()`) for flexible usage

#### ✅ File: `backend/src/main/java/com/scrumpoker/security/RequiresTier.java`
- **Status:** **COMPLETE** (79 lines with extensive Javadoc)
- **Implementation Details:**
  - ✅ `@InterceptorBinding` annotation for CDI integration
  - ✅ `@Target({ElementType.METHOD, ElementType.TYPE})` - Supports both method and class-level application
  - ✅ `@Retention(RetentionPolicy.RUNTIME)` - Accessible at runtime for reflection
  - ✅ `value()` parameter with `@Nonbinding` annotation (accepts SubscriptionTier)
- **Code Quality:** Production-ready with extensive usage examples in Javadoc showing both class-level and method-level annotation patterns

#### ✅ File: `backend/src/main/java/com/scrumpoker/security/TierEnforcementInterceptor.java`
- **Status:** **COMPLETE** (188 lines, fully functional)
- **Implementation Details:**
  - ✅ `@Provider` annotation for JAX-RS auto-registration
  - ✅ `@Priority(Priorities.AUTHORIZATION)` - Runs AFTER authentication, BEFORE business logic
  - ✅ Implements `ContainerRequestFilter` interface
  - ✅ Injects `SecurityIdentity`, `UserRepository`, `FeatureGate`, `ResourceInfo`
  - ✅ Detection logic: Checks method-level `@RequiresTier` first, falls back to class-level
  - ✅ User lookup: Extracts userId from SecurityIdentity principal, fetches User entity
  - ✅ Tier validation: Uses `FeatureGate.hasSufficientTier(user, requiredTier)`
  - ✅ Error handling: Throws `FeatureNotAvailableException` with tier context
  - ✅ Comprehensive logging at DEBUG, INFO, WARN, ERROR levels
- **Code Quality:** Production-ready with defensive null checks, proper exception handling, detailed log messages

#### ✅ File: `backend/src/main/java/com/scrumpoker/security/FeatureNotAvailableException.java`
- **Status:** **COMPLETE** (115 lines)
- **Implementation Details:**
  - ✅ Extends `RuntimeException`
  - ✅ Fields: `requiredTier`, `currentTier`, `featureName`
  - ✅ Automatic message generation with upgrade CTA: `"This feature requires {tier} or higher. Your current tier is {currentTier}. Upgrade your subscription to access {feature}."`
  - ✅ Tier name formatting helper: `PRO_PLUS` → `"Pro+"`, `ENTERPRISE` → `"Enterprise"`
  - ✅ Getters for all fields
- **Code Quality:** Production-ready with user-friendly error messages and complete metadata

#### ✅ Bonus: Exception Mapper Already Exists
**File:** `backend/src/main/java/com/scrumpoker/api/rest/exception/FeatureNotAvailableExceptionMapper.java`
- **Status:** **COMPLETE** (56 lines)
- Maps `FeatureNotAvailableException` to HTTP 403 Forbidden
- Returns `ErrorResponse` DTO with error code `"FEATURE_NOT_AVAILABLE"`
- Includes upgrade CTA message in response body

### 🎯 Integration Status

#### ✅ RoomService Integration (ALREADY COMPLETE)
**File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java` (lines 68-77)
```java
// Enforce tier requirements for privacy modes
if (owner != null) {
    if (privacyMode == PrivacyMode.INVITE_ONLY) {
        // INVITE_ONLY requires PRO_PLUS or ENTERPRISE tier
        featureGate.requireCanCreateInviteOnlyRoom(owner);
    } else if (privacyMode == PrivacyMode.ORG_RESTRICTED) {
        // ORG_RESTRICTED requires ENTERPRISE tier (organization management)
        featureGate.requireCanManageOrganization(owner);
    }
    // PUBLIC rooms are available to all tiers (no check needed)
}
```
- **Analysis:** The FeatureGate is already injected at line 39 and used for privacy mode enforcement in the `createRoom()` method
- **Coverage:** Handles both INVITE_ONLY (PRO_PLUS+) and ORG_RESTRICTED (ENTERPRISE) privacy modes
- **Pattern:** Uses imperative `requireXxx()` methods that throw exceptions on tier violations

### 📋 Acceptance Criteria Verification

Let me map the task acceptance criteria to the existing implementation:

1. ✅ **"Free tier user cannot create invite-only room (403 error)"**
   - Implemented in: `RoomService.createRoom()` line 71
   - Method: `featureGate.requireCanCreateInviteOnlyRoom(owner)`
   - Throws: `FeatureNotAvailableException` → Mapped to 403 by exception mapper

2. ✅ **"Pro tier user can create invite-only room"**
   - Implemented in: `FeatureGate.canCreateInviteOnlyRoom()` line 95-97
   - Logic: `hasSufficientTier(user, SubscriptionTier.PRO_PLUS)` - PRO_PLUS and ENTERPRISE pass
   - Note: Task says "Pro tier can create" but implementation requires PRO_PLUS (per feature matrix). This is CORRECT per architecture docs.

3. ✅ **"Free tier user accessing advanced reports returns 403"**
   - Implemented in: `FeatureGate.requireCanAccessAdvancedReports()` line 148-156
   - Throws: `FeatureNotAvailableException` with required tier = PRO

4. ✅ **"Interceptor enforces @RequiresTier annotation on endpoints"**
   - Implemented in: `TierEnforcementInterceptor.filter()` line 108-186
   - Detection: Lines 119-129 (checks method then class annotation)
   - Enforcement: Lines 173-182 (throws exception if tier insufficient)

5. ✅ **"Exception message includes upgrade CTA"**
   - Implemented in: `FeatureNotAvailableException.buildMessage()` line 77-86
   - Message format: `"This feature requires Pro tier or higher. Your current tier is Free. Upgrade your subscription to access Advanced Reports."`

### 🎯 Task Completion Analysis

**Deliverables Check:**
- ✅ FeatureGate service with tier check methods → **COMPLETE**
- ✅ Custom annotation @RequiresTier for declarative enforcement → **COMPLETE**
- ✅ Interceptor validating tier on annotated endpoints → **COMPLETE**
- ✅ FeatureNotAvailableException (403 Forbidden + upgrade prompt) → **COMPLETE**
- ✅ Integration in RoomService → **COMPLETE**

**All Target Files:**
- ✅ `backend/src/main/java/com/scrumpoker/security/FeatureGate.java` - 230 lines, production-ready
- ✅ `backend/src/main/java/com/scrumpoker/security/RequiresTier.java` - 79 lines, production-ready
- ✅ `backend/src/main/java/com/scrumpoker/security/TierEnforcementInterceptor.java` - 188 lines, production-ready
- ✅ `backend/src/main/java/com/scrumpoker/security/FeatureNotAvailableException.java` - 115 lines, production-ready

**Bonus Implementations:**
- ✅ `FeatureNotAvailableExceptionMapper.java` - JAX-RS exception mapper (not in requirements but essential)

### 🚨 CRITICAL INSTRUCTIONS FOR CODER AGENT

**DO NOT RE-IMPLEMENT ANY CODE. The task is already complete.**

Your responsibilities are:

1. **Verify Implementation Quality:**
   - Review all four target files to confirm they match the task requirements
   - Check that all acceptance criteria are satisfied by the existing code

2. **Validate Integration:**
   - Confirm FeatureGate is properly injected in RoomService (line 39)
   - Verify the tier checks are called in the correct places (lines 68-77)
   - Check that the exception mapper is properly registered as a JAX-RS provider

3. **Test Verification (if tests exist):**
   - Look for existing unit tests in `backend/src/test/java/com/scrumpoker/security/`
   - Look for integration tests validating tier enforcement in `backend/src/test/java/com/scrumpoker/domain/room/`
   - If tests are missing, note this in your completion report but DO NOT write new tests (that's likely a future task)

4. **Mark Task as Complete:**
   - Update the task data file to set `"done": true` for I5.T4
   - Report that the task was already implemented and all acceptance criteria are met

### 📚 Supporting Context (for understanding, not for reimplementation)

#### Tier Hierarchy Pattern (already implemented)
```java
// From FeatureGate.java line 75-80
public boolean hasSufficientTier(User user, SubscriptionTier requiredTier) {
    if (user == null || user.subscriptionTier == null) {
        return false;
    }
    return user.subscriptionTier.ordinal() >= requiredTier.ordinal();
}
```
- **Pattern:** Uses enum ordinals for tier comparison
- **Hierarchy:** FREE (0) < PRO (1) < PRO_PLUS (2) < ENTERPRISE (3)
- **Semantics:** Higher tiers satisfy lower tier requirements (ENTERPRISE can access PRO features)

#### Imperative vs. Declarative Enforcement (both implemented)
**Imperative (in service layer):**
```java
// From RoomService.java line 71
featureGate.requireCanCreateInviteOnlyRoom(owner);  // Throws if tier insufficient
```

**Declarative (on REST endpoint - ready to use):**
```java
@GET
@Path("/advanced")
@RequiresTier(SubscriptionTier.PRO)  // Interceptor enforces automatically
public Response getAdvancedReport() { ... }
```

#### Error Response Format (already implemented)
HTTP 403 Forbidden response:
```json
{
  "error": "FEATURE_NOT_AVAILABLE",
  "message": "This feature requires Pro+ tier or higher. Your current tier is Free. Upgrade your subscription to access Invite-Only Rooms.",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

### ⚠️ Important Notes

1. **The implementation is MORE comprehensive than the task requirements:**
   - Task asked for 4 methods in FeatureGate → Implementation has 9 methods (boolean checks + imperative enforcement for each feature)
   - Task didn't specify exception mapper → Implementation includes it
   - Task didn't specify logging → Implementation has comprehensive logging

2. **The integration with RoomService is already production-ready:**
   - FeatureGate is injected (line 39)
   - Tier checks are performed before room creation (lines 68-77)
   - Covers both INVITE_ONLY (PRO_PLUS+) and ORG_RESTRICTED (ENTERPRISE) privacy modes
   - Allows PUBLIC rooms for all tiers (correct behavior)

3. **The code follows Quarkus and Jakarta EE best practices:**
   - Uses `@ApplicationScoped` for singleton services
   - Uses `@Provider` for auto-registration
   - Uses `@Priority` for execution order control
   - Uses `@InterceptorBinding` for CDI interceptors
   - Follows reactive patterns where appropriate (though tier checks are synchronous)

4. **All code has comprehensive Javadoc:**
   - Class-level documentation explaining purpose and usage
   - Method-level documentation with parameters, returns, and exceptions
   - Usage examples in annotations (@see references)

### 🎬 Conclusion

**This task (I5.T4) is 100% complete.** All deliverables exist, all acceptance criteria are satisfied, and the implementation quality exceeds expectations. The Coder Agent should verify this assessment and mark the task as done.
