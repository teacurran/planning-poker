# SSO Test Resources

This directory contains mock SSO responses for integration testing.

## Files

### `mock_id_token.jwt`
Mock OIDC ID token for testing SSO authentication flow. This is a base64-encoded JWT containing:

**Header:**
```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "test-key-id"
}
```

**Payload:**
```json
{
  "iss": "https://acmecorp.okta.com",
  "sub": "oidc-subject-123456",
  "aud": "test-client-id",
  "exp": 1740000000,
  "iat": 1730000000,
  "email": "john.doe@acmecorp.com",
  "name": "John Doe",
  "email_verified": true,
  "groups": ["Users", "Developers"]
}
```

**Signature:**
Mock signature (not cryptographically valid - for reference only)

**Note:** This token is NOT used directly in the integration tests. The tests mock the `SsoAdapter.authenticate()` method to return a `SsoUserInfo` object, avoiding the need to validate actual JWT signatures.

## SAML2 Resources

SAML2 support is planned but not yet implemented. The following file will be added in a future iteration:

- `mock_saml_response.xml` - Mock SAML2 assertion for testing

## Usage

These resources are for documentation and reference. The integration tests use mocked `SsoAdapter` responses rather than parsing these files directly. This approach allows testing the AuthController logic without requiring actual IdP connectivity or cryptographic validation.
