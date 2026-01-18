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

### `mock_saml_response.xml`
Mock SAML2 response for testing SSO authentication flow. This is a Base64-encodable XML document containing:

**Structure:**
- `<samlp:Response>`: Root element with status and assertion
- `<saml:Assertion>`: Contains signature, subject (NameID), conditions, attributes
- `<Signature>`: XML digital signature (mock - not cryptographically valid)
- `<saml:Subject>`: User identifier (NameID format: URI)
- `<saml:Conditions>`: Validity period (NotBefore: 2026-01-18T09:55:00Z, NotOnOrAfter: 2026-01-18T10:05:00Z)
- `<saml:AttributeStatement>`: User attributes (email, displayName, groups)

**User Data:**
- NameID: `https://acmecorp.okta.com/users/john.doe`
- Email: `john.doe@acmecorp.com`
- Display Name: `John Doe`
- Groups: `["Users", "Developers"]`

**Note:** This file is for documentation and reference only. The integration tests use mocked `SsoAdapter` responses rather than parsing this XML file directly. This approach allows testing the AuthController logic without requiring actual SAML XML parsing or cryptographic validation.

## Usage

These resources are for documentation and reference. The integration tests use mocked `SsoAdapter` responses rather than parsing these files directly. This approach allows testing the AuthController logic without requiring actual IdP connectivity or cryptographic validation.
