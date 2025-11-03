# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Extend `AuthController` to handle SSO authentication. New endpoint: `POST /api/v1/auth/sso/callback` (handle OIDC/SAML2 callback, validate assertion, extract user info, find or create user with JIT provisioning, assign to organization based on email domain, generate JWT tokens, return TokenPair). Integrate `SsoAdapter`, `OrganizationService`. JIT provisioning: if user doesn't exist, create User entity with SSO provider info, auto-add to organization if email domain matches. Domain-based org assignment (user with `@company.com` joins org with domain `company.com`). Log SSO login to AuditLog.

---

## Issues Detected

*   **Compilation Error:** The code uses `jakarta.servlet.http.HttpServletRequest` on line 26 and line 391 of `AuthController.java`, which causes compilation failures. This package is not available in the project's dependencies and is not the correct approach for JAX-RS REST endpoints.
    - Error: `package jakarta.servlet.http does not exist`
    - Error: `cannot find symbol - class HttpServletRequest`

*   **Incorrect HTTP Context Access Pattern:** The implementation attempts to inject `HttpServletRequest` to extract IP address and User-Agent headers, but this is not the standard JAX-RS pattern used in this Quarkus project. The existing OAuth callback endpoint at line 119 (`oauthCallback`) does NOT use any HTTP request context injection and doesn't capture IP/User-Agent for audit logging.

*   **Missing Pattern Reference:** The existing `TierEnforcementInterceptor.java` shows the correct pattern using `ContainerRequestContext` for accessing HTTP request details in JAX-RS filters, but REST endpoint methods in this project don't directly inject request context.

---

## Best Approach to Fix

You MUST modify the `ssoCallback` method in `src/main/java/com/scrumpoker/api/rest/AuthController.java` to remove the dependency on `HttpServletRequest`:

1. **Remove the HttpServletRequest import and parameter:**
   - Delete line 26: `import jakarta.servlet.http.HttpServletRequest;`
   - Change the method signature on line 390-391 from:
     ```java
     public Uni<Response> ssoCallback(@Valid final SsoCallbackRequest request,
                                       @Context final HttpServletRequest httpRequest)
     ```
     to:
     ```java
     public Uni<Response> ssoCallback(@Valid final SsoCallbackRequest request)
     ```

2. **Handle audit logging without HTTP context:**
   Since the existing OAuth callback doesn't capture IP/User-Agent, and audit logging for SSO is required, you have two options:

   **Option A (Recommended - Simple & Consistent):** Call `auditLogService.logSsoLogin()` with `null` for IP address and User-Agent:
   ```java
   auditLogService.logSsoLogin(
       organization.orgId,
       user.userId,
       null,  // IP address not available in endpoint context
       null   // User-Agent not available in endpoint context
   );
   ```

   **Option B (More Complex):** If IP/User-Agent are strictly required, inject `@Context ContainerRequestContext requestContext` instead of `HttpServletRequest`, then extract headers:
   ```java
   public Uni<Response> ssoCallback(@Valid final SsoCallbackRequest request,
                                     @Context final ContainerRequestContext requestContext) {
       // Extract headers
       String ipAddress = AuditLogService.extractIpAddress(
           requestContext.getHeaderString("X-Forwarded-For"),
           requestContext.getHeaderString("X-Real-IP"),
           null  // requestContext doesn't expose remote address
       );
       String userAgent = requestContext.getHeaderString("User-Agent");
       // ... rest of implementation
   }
   ```

3. **Remove IP/User-Agent extraction code:**
   - Delete lines 404-410 that extract IP address and user agent from `httpRequest`

4. **Keep all other logic intact:**
   - The SSO authentication flow is correct (org lookup → authenticate → provision user → add member → generate tokens → audit log)
   - The domain matching validation is correct
   - The reactive chain is properly structured
   - The error handling is appropriate

5. **Verify compilation:**
   After making changes, the code must compile successfully with `mvn clean compile -DskipTests`

---

## Summary

The core issue is using `HttpServletRequest` which is not available. Replace it with either `ContainerRequestContext` (Option B) or remove HTTP context injection entirely and pass `null` to audit logging (Option A - simpler and consistent with OAuth callback pattern). All other implementation logic is correct and should remain unchanged.
