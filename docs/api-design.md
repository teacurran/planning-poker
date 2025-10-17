# Planning Poker API Design

## Overview

The Planning Poker API is a RESTful JSON API following OpenAPI 3.1 specification. It provides endpoints for user authentication, room management, subscription billing, reporting, and enterprise organization management.

**OpenAPI Specification:** [`/api/openapi.yaml`](../api/openapi.yaml)

**Base URL:** `https://api.planningpoker.example.com`

**API Version:** v1 (all endpoints use `/api/v1/` prefix)

---

## Design Philosophy

### RESTful Principles

- **Resource-oriented URLs**: Endpoints represent resources (users, rooms, subscriptions)
- **HTTP verbs**: Standard methods (GET, POST, PUT, DELETE) for CRUD operations
- **Stateless**: Each request contains all necessary information (JWT token)
- **JSON payloads**: All request/response bodies use `application/json`

### API Versioning

URL-based versioning with `/api/v1/` prefix ensures:
- Backward compatibility during iterative releases
- Clear API evolution path
- Client SDK version pinning

**Migration Path:** Breaking changes will increment version (`/api/v2/`). Deprecated versions supported for 12 months post-announcement.

### Error Handling

All error responses follow a consistent structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error description",
  "timestamp": "2025-01-15T10:30:00Z",
  "details": {}
}
```

**Standard HTTP Status Codes:**
- `400 Bad Request` - Invalid request parameters or validation errors
- `401 Unauthorized` - Missing or invalid authentication token
- `403 Forbidden` - Insufficient permissions or subscription tier
- `404 Not Found` - Resource not found or soft-deleted
- `500 Internal Server Error` - Unexpected server error

### Validation Rules

All input validation follows database schema constraints:
- **String length limits**: Enforced via `maxLength` (e.g., email: 255 chars)
- **Format validation**: Email, UUID, URI formats validated
- **Pattern matching**: Room IDs must match `^[a-z0-9]{6}$` pattern
- **Enum constraints**: Fixed values for tiers, roles, statuses

---

## Authentication & Security

### OAuth2 Authorization Code Flow

**Step 1: Redirect to OAuth Provider**
```
https://accounts.google.com/o/oauth2/v2/auth?
  client_id={CLIENT_ID}&
  redirect_uri={REDIRECT_URI}&
  response_type=code&
  scope=openid email profile
```

**Step 2: Exchange Authorization Code**
```bash
POST /api/v1/auth/oauth/callback
Content-Type: application/json

{
  "code": "4/0AX4XfWh...",
  "provider": "google",
  "redirectUri": "https://planningpoker.example.com/auth/callback"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "v1.MR5tqKz...",
  "expiresIn": 900,
  "tokenType": "Bearer",
  "user": {
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "email": "alice@example.com",
    "displayName": "Alice Smith",
    "subscriptionTier": "PRO"
  }
}
```

### JWT Token Management

**Access Token:**
- Expiry: 15 minutes
- Usage: Include in `Authorization: Bearer {token}` header
- Claims: `sub` (userId), `email`, `tier`, `exp`, `iat`

**Refresh Token:**
- Expiry: 30 days
- Usage: Single-use, rotated on each refresh
- Endpoint: `POST /api/v1/auth/refresh`

**Token Refresh Example:**
```bash
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "v1.MR5tqKz..."
}
```

### WebSocket Authentication

WebSocket connections use JWT access tokens passed as query parameters:

```javascript
const ws = new WebSocket(`wss://api.planningpoker.example.com/ws/rooms/abc123?token=${accessToken}`);
```

**Note:** WebSocket protocol details are documented separately in [websocket-protocol.md](./websocket-protocol.md).

---

## Rate Limiting

Rate limits vary by subscription tier:

| Tier | Requests/Hour | Burst Limit |
|------|---------------|-------------|
| Anonymous | 100 | 20 |
| Free | 1,000 | 50 |
| Pro / Pro Plus | 5,000 | 100 |
| Enterprise | 10,000 | 200 |

**Rate Limit Headers:**
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1642252800
```

**429 Response:**
```json
{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded. Try again in 3600 seconds.",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## API Domains

### 1. Authentication (`/api/v1/auth/*`)

OAuth2 authentication with Google and Microsoft providers.

**Endpoints:**
- `POST /api/v1/auth/oauth/callback` - Exchange OAuth code for tokens
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/logout` - Revoke refresh token

**Reference:** [openapi.yaml - Authentication](../api/openapi.yaml#L70)

---

### 2. User Management (`/api/v1/users/*`)

User profile and preference management.

**Endpoints:**
- `GET /api/v1/users/{userId}` - Get user profile
- `PUT /api/v1/users/{userId}` - Update profile (display name, avatar)
- `GET /api/v1/users/{userId}/preferences` - Get preferences
- `PUT /api/v1/users/{userId}/preferences` - Update preferences (theme, default room config)

**Permissions:** Users can only modify their own profile and preferences.

**Reference:** [openapi.yaml - Users](../api/openapi.yaml#L180)

---

### 3. Room Management (`/api/v1/rooms/*`)

Estimation room lifecycle and configuration.

**Key Concepts:**
- **Room ID Format**: 6-character nanoid (e.g., `abc123`) for short, shareable URLs
- **Ownership**: Rooms can be owned by authenticated users or anonymous (ephemeral)
- **Privacy Modes**: PUBLIC, INVITE_ONLY, ORG_RESTRICTED

**Endpoints:**
- `POST /api/v1/rooms` - Create room (authenticated or anonymous)
- `GET /api/v1/rooms/{roomId}` - Get room configuration
- `PUT /api/v1/rooms/{roomId}/config` - Update room settings (host only)
- `DELETE /api/v1/rooms/{roomId}` - Soft delete room (owner only)
- `GET /api/v1/users/{userId}/rooms` - List user's rooms

**Room Configuration:**
- Deck type (Fibonacci, T-shirt sizes, Powers of 2, Custom)
- Timer settings (enabled, duration, reveal behavior)
- Privacy and participant permissions

**Reference:** [openapi.yaml - Rooms](../api/openapi.yaml#L290)

---

### 4. Subscription & Billing (`/api/v1/subscriptions/*`)

Stripe integration for subscription management.

**Tiers:**
- **FREE**: 5 active rooms, 30-day history, basic features
- **PRO**: Unlimited rooms, 90-day history, detailed reports, CSV export
- **PRO_PLUS**: Unlimited history, PDF exports, priority support
- **ENTERPRISE**: SSO, audit logs, custom branding, SLA

**Endpoints:**
- `GET /api/v1/subscriptions/{userId}` - Get subscription status
- `POST /api/v1/subscriptions/checkout` - Create Stripe checkout session
- `POST /api/v1/subscriptions/{subscriptionId}/cancel` - Cancel subscription
- `POST /api/v1/subscriptions/webhook` - Stripe webhook handler
- `GET /api/v1/billing/invoices` - List payment history

**Checkout Flow:**
1. Client calls `POST /api/v1/subscriptions/checkout` with target tier
2. Server returns Stripe Checkout URL
3. Client redirects user to Stripe
4. Stripe webhook updates subscription status
5. Client polls `GET /api/v1/subscriptions/{userId}` for status

**Reference:** [openapi.yaml - Subscriptions](../api/openapi.yaml#L455)

---

### 5. Reporting & Analytics (`/api/v1/reports/*`)

Session history, detailed reports, and export jobs.

**Tier Restrictions:**
- **Free**: Last 30 days, summary only (average/median)
- **Pro**: Last 90 days, detailed round-by-round data
- **Pro Plus/Enterprise**: Unlimited history, CSV/PDF export

**Endpoints:**
- `GET /api/v1/reports/sessions` - List session history (paginated, filterable)
- `GET /api/v1/reports/sessions/{sessionId}` - Detailed session report
- `POST /api/v1/reports/export` - Create export job (CSV/PDF)
- `GET /api/v1/jobs/{jobId}` - Poll export job status

**Export Job Workflow:**
1. Client calls `POST /api/v1/reports/export` with format and session IDs
2. Server returns job ID and status `PENDING`
3. Client polls `GET /api/v1/jobs/{jobId}` until status is `COMPLETED`
4. Download URL provided (expires in 24 hours)

**Reference:** [openapi.yaml - Reports](../api/openapi.yaml#L625)

---

### 6. Organization Management (`/api/v1/organizations/*`)

Enterprise SSO workspaces and member management.

**Features (Enterprise Tier Only):**
- OIDC/SAML2 SSO integration
- Custom branding (logo, colors)
- Member role management (ADMIN, MEMBER)
- Audit log trail

**Endpoints:**
- `POST /api/v1/organizations` - Create organization
- `GET /api/v1/organizations/{orgId}` - Get organization settings
- `PUT /api/v1/organizations/{orgId}/sso` - Configure SSO (ADMIN only)
- `POST /api/v1/organizations/{orgId}/members` - Invite member (ADMIN only)
- `DELETE /api/v1/organizations/{orgId}/members/{userId}` - Remove member
- `GET /api/v1/organizations/{orgId}/audit-logs` - Query audit trail (ADMIN only)

**SSO Configuration:**
- **OIDC**: Requires issuer, client ID/secret, authorization/token/JWKS endpoints
- **SAML2**: Requires entity ID, SSO URL, X.509 certificate

**Reference:** [openapi.yaml - Organizations](../api/openapi.yaml#L780)

---

## Pagination

List endpoints use cursor-based pagination with query parameters:

**Parameters:**
- `page` (integer, 0-indexed, default: 0)
- `size` (integer, 1-100, default: 20)

**Response Structure:**
```json
{
  "items": [...],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

**Example:**
```bash
GET /api/v1/users/123e4567-e89b-12d3-a456-426614174000/rooms?page=1&size=50
```

---

## Testing & Documentation Tools

### Swagger UI

Interactive API documentation and testing interface:

```bash
# View OpenAPI spec in Swagger UI
open https://editor.swagger.io/

# Import: /api/openapi.yaml
```

### Validation Tools

**Spectral CLI** (OpenAPI linting):
```bash
npm install -g @stoplight/spectral-cli
spectral lint api/openapi.yaml
```

**Swagger Editor** (online validation):
- Visit https://editor.swagger.io/
- Import `api/openapi.yaml`
- Check for validation errors in right panel

### Client SDK Generation

**OpenAPI Generator:**
```bash
# Generate TypeScript client for React frontend
openapi-generator-cli generate \
  -i api/openapi.yaml \
  -g typescript-axios \
  -o frontend/src/api/client
```

**Supported Languages:** TypeScript, Java, Python, Go, Ruby, PHP, Kotlin, Swift

---

## Changelog

### v1.0.0 (2025-01-15)
- Initial API specification
- OAuth2 authentication with Google/Microsoft
- Room CRUD operations
- Subscription management with Stripe
- Session reporting and export
- Enterprise organization management

---

## Support

**API Documentation:** https://docs.planningpoker.example.com/api

**Issue Tracker:** https://github.com/planningpoker/api/issues

**Contact:** api-support@planningpoker.example.com

---

*Generated with Planning Poker API v1.0.0*
