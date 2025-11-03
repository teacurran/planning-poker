# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create `FeatureGate` service enforcing tier-based feature access. Methods: `canCreateInviteOnlyRoom(User)` (Pro+ or Enterprise), `canAccessAdvancedReports(User)` (Pro or higher), `canRemoveAds(User)` (Pro or higher), `canManageOrganization(User)` (Enterprise only). Inject into REST controllers and services. Throw `FeatureNotAvailableException` when user attempts unavailable feature. Implement `@RequiresTier(SubscriptionTier.PRO)` custom annotation for declarative enforcement on REST endpoints. Create interceptor validating tier requirements.

**Acceptance Criteria:**
- Free tier user cannot create invite-only room (403 error)
- Pro tier user can create invite-only room
- Free tier user accessing advanced reports returns 403
- Interceptor enforces @RequiresTier annotation on endpoints
- Exception message includes upgrade CTA (e.g., "Upgrade to Pro to access this feature")

---

## Issues Detected

### **Critical Issue: Incorrect Annotation Type for JAX-RS Filter**
*   The `@RequiresTier` annotation is currently using `@InterceptorBinding` (line 63 in RequiresTier.java), which is designed for CDI interceptors. However, `TierEnforcementInterceptor` implements `ContainerRequestFilter`, which is a JAX-RS filter. This is a fundamental mismatch.
*   In JAX-RS, filters should use `@NameBinding` annotation instead of `@InterceptorBinding`.
*   As a result, the current implementation will NOT work correctly - the filter will run on ALL requests regardless of whether the `@RequiresTier` annotation is present or not.

### **Checkstyle Violations in New Files**
*   **RequiresTier.java:** Multiple line length violations (lines exceed 80 characters: lines 13, 15, 16, 17, 42, 43, 48, 49, 54, 55, 71)
*   **TierEnforcementInterceptor.java:**
    - Line length violations (lines 21, 23, 24, 25, 31, 33, 34, 35, 36, 42, 65, 69, 70, 82, 99, 104, 126, 127, 137, 138, 149, 152, 165, 169, 174, 175, 184, 185)
    - Missing Javadoc comments for fields: `LOG` (line 82), `securityIdentity` (line 84), `userRepository` (line 87), `featureGate` (line 90), `resourceInfo` (line 93)
    - Visibility violations for injected fields (must be private with accessor methods): `securityIdentity`, `userRepository`, `featureGate`
*   **FeatureNotAvailableException.java:**
    - Line length violations (lines 8, 9, 10, 11, 28, 29, 41, 42, 43, 58, 59, 60, 61, 77, 78, 80, 81)
    - Missing Javadoc for private fields: `requiredTier`, `currentTier`, `featureName`
    - Visibility violations for fields (must be private with accessor methods)
*   **FeatureGate.java:**
    - Line length violations (lines 10, 11, 15, 16, 17, 18, 32, 52, 53, 54, 89, 109, 111, 119, 122, 127, 131, 132, 148, 150, 159, 161, 165, 181, 183, 192, 194, 197, 202, 204, 220, 222)

### **RoomService Integration**
*   The RoomService integration is correct functionally, but it has introduced new checkstyle violations:
    - Line 52: Line exceeds 80 characters
    - Line 70: Line exceeds 80 characters
    - Line 151: Line exceeds 80 characters
    - Line 172: Line exceeds 80 characters

---

## Best Approach to Fix

### Step 1: Fix the @RequiresTier Annotation (CRITICAL)

You MUST modify `backend/src/main/java/com/scrumpoker/security/RequiresTier.java`:

1. Replace `@InterceptorBinding` with `@NameBinding` (import `jakarta.ws.rs.NameBinding`)
2. Remove the import for `jakarta.interceptor.InterceptorBinding`
3. Remove the import for `jakarta.enterprise.util.Nonbinding`
4. Remove the `@Nonbinding` annotation from the `value()` method (not needed for `@NameBinding`)

**Example:**
```java
import jakarta.ws.rs.NameBinding;  // <- Use this instead of InterceptorBinding
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@NameBinding  // <- Use this instead of @InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresTier {
    SubscriptionTier value();  // <- Remove @Nonbinding
}
```

### Step 2: Update TierEnforcementInterceptor to Use @RequiresTier Binding

You MUST modify `backend/src/main/java/com/scrumpoker/security/TierEnforcementInterceptor.java`:

1. Add the `@RequiresTier` annotation to the class itself (with a dummy value that will be overridden by method/class annotations)
2. This tells JAX-RS that this filter should only run on endpoints annotated with `@RequiresTier`

**Example:**
```java
@Provider
@Priority(Priorities.AUTHORIZATION)
@RequiresTier(SubscriptionTier.FREE)  // <- Add this line (the value doesn't matter, it just binds the filter)
public class TierEnforcementInterceptor implements ContainerRequestFilter {
    // ... rest of the code
}
```

### Step 3: Fix All Checkstyle Violations

You MUST fix all checkstyle violations in the new files:

1. **Line length violations:** Break long lines to stay under 80 characters. Use line breaks in Javadoc comments and method signatures.
2. **Missing Javadoc for fields:** Add Javadoc comments for ALL fields in TierEnforcementInterceptor and FeatureNotAvailableException.
3. **Field visibility violations:** Change injected fields in TierEnforcementInterceptor to `private` and rely on CDI's ability to inject into private fields (which Quarkus supports).

**For injected fields, use this pattern:**
```java
@Inject
private SecurityIdentity securityIdentity;  // <- Make it private

@Inject
private UserRepository userRepository;  // <- Make it private

@Inject
private FeatureGate featureGate;  // <- Make it private

@Context
private ResourceInfo resourceInfo;  // <- Make it private
```

**For the LOG field, add Javadoc:**
```java
/**
 * Logger for tier enforcement operations.
 */
private static final Logger LOG = Logger.getLogger(TierEnforcementInterceptor.class);
```

4. **Fix field visibility in FeatureNotAvailableException:** The fields `requiredTier`, `currentTier`, and `featureName` must remain private (they already are), but you need to add Javadoc comments for them.

**Example:**
```java
/**
 * The minimum subscription tier required for the feature.
 */
private final SubscriptionTier requiredTier;

/**
 * The user's current subscription tier.
 */
private final SubscriptionTier currentTier;

/**
 * The name of the feature being accessed.
 */
private final String featureName;
```

5. **Fix RoomService checkstyle violations:** Break long lines in the new tier enforcement checks into multiple lines.

### Step 4: Verify the Fix

After making these changes:

1. Run `mvn checkstyle:check` and confirm there are NO new violations in the files you modified
2. The interceptor pattern should now work correctly: the filter will ONLY run on endpoints with `@RequiresTier` annotation
3. All existing functionality should remain intact

---

## Important Notes

- The most critical fix is changing from `@InterceptorBinding` to `@NameBinding`. Without this fix, the declarative tier enforcement will not work correctly.
- All checkstyle violations MUST be fixed. The codebase has a strict quality standard.
- Do NOT modify any existing checkstyle violations in other files - only fix violations in the files you created or modified for this task.
- After fixing these issues, the implementation should fully meet all acceptance criteria.
