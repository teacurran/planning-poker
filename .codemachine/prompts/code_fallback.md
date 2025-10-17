# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create `JwtTokenService` for JWT access token and refresh token management. Implement token generation: create access token with claims (sub: userId, email, roles, tier, exp: 1 hour), create refresh token (UUID stored in Redis with 30-day TTL). Implement token validation: verify signature (RSA key), check expiration, extract claims. Implement token refresh: validate refresh token from Redis, generate new access token, rotate refresh token. Use SmallRye JWT library. Store RSA private key in application config (production: Kubernetes Secret), public key for validation.

---

## Issues Detected

*   **CRITICAL SECURITY FLAW:** The `validateAccessToken` method in `JwtTokenService.java` (line 198) calls `.setSkipSignatureVerification()`, which completely bypasses signature validation. This means ANY token with the correct JSON structure will be accepted, even if signed with a different key or not signed at all. This is a critical security vulnerability that violates the acceptance criteria "Signature validation uses RSA public key correctly".

---

## Best Approach to Fix

You MUST modify the `validateAccessToken` method in `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java` to properly validate JWT signatures using the RSA public key.

**Option 1 (Recommended): Use SmallRye JWT's built-in validation**

Replace the manual jose4j validation with SmallRye JWT's built-in `DefaultJWTParser`:

```java
public Uni<com.scrumpoker.security.JwtClaims> validateAccessToken(String token) {
    if (token == null || token.isBlank()) {
        throw new IllegalArgumentException("Token cannot be null or blank");
    }

    LOG.debugf("Validating access token (first 10 chars: %s...)",
               token.substring(0, Math.min(10, token.length())));

    return Uni.createFrom().item(() -> {
        try {
            // Use SmallRye JWT parser which automatically validates signature using config
            DefaultJWTParser parser = new DefaultJWTParser();
            JsonWebToken jwt = parser.parse(token);

            // Verify issuer matches expected value
            if (!issuer.equals(jwt.getIssuer())) {
                throw new RuntimeException("Invalid token issuer");
            }

            // Extract claims
            UUID userId = UUID.fromString(jwt.getSubject());
            String email = jwt.getClaim("email");
            List<String> roles = jwt.getClaim("roles");
            String tier = jwt.getClaim("tier");

            LOG.infof("Access token validated successfully for user: %s", userId);
            return new com.scrumpoker.security.JwtClaims(userId, email, roles, tier);

        } catch (Exception e) {
            LOG.errorf(e, "Token validation failed: %s", e.getMessage());
            throw new RuntimeException("Invalid or expired token: " + e.getMessage(), e);
        }
    });
}
```

**Option 2: Properly configure jose4j validation**

If you prefer to use jose4j directly, you MUST properly configure the RSA public key for signature verification:

1. Inject the public key location config property
2. Load the public key from the file
3. Configure JwtConsumerBuilder with `.setVerificationKey(publicKey)` instead of `.setSkipSignatureVerification()`

**CRITICAL:** Do NOT use `.setSkipSignatureVerification()` - this is only acceptable for testing, never for production code.

After making the fix, ensure that:
1. The code compiles without errors
2. All tests pass
3. Token signature validation actually works (tokens signed with wrong key should be rejected)
