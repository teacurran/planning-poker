# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T1",
  "iteration_id": "I2",
  "iteration_goal": "Implement foundational domain services (Room Service, basic User Service), define REST API contracts (OpenAPI specification), and establish WebSocket protocol specification to enable frontend integration and parallel feature development.",
  "description": "Create comprehensive OpenAPI 3.1 YAML specification documenting all planned REST API endpoints. Define schemas for DTOs (UserDTO, RoomDTO, SubscriptionDTO, etc.), request bodies, response structures, error codes (400, 401, 403, 404, 500 with standardized error schema). Document endpoints for: user management (`/api/v1/users/*`), room CRUD (`/api/v1/rooms/*`), authentication (`/api/v1/auth/*`), subscriptions (`/api/v1/subscriptions/*`), reporting (`/api/v1/reports/*`), organizations (`/api/v1/organizations/*`). Include security schemes (Bearer JWT, OAuth2 flows). Add descriptions, examples, and validation rules (min/max lengths, patterns, required fields).",
  "agent_type_hint": "DocumentationAgent",
  "inputs": "*   REST API endpoint overview from architecture blueprint (Section 4 - API Design)\n        *   Entity models from I1.T4 (for DTO schema definitions)\n        *   Authentication/authorization requirements",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   OpenAPI 3.1 YAML file with 30+ endpoint definitions\n        *   Complete schema definitions for all DTOs (User, Room, Vote, Subscription, Organization, etc.)\n        *   Error response schema with standardized structure (`{\"error\": \"...\", \"message\": \"...\", \"timestamp\": \"...\"}`)\n        *   Security scheme definitions (JWT Bearer, OAuth2 authorization code flow)\n        *   Request/response examples for critical endpoints\n        *   Validation rules in schemas (string formats, numeric ranges, enum values)",
  "acceptance_criteria": "*   OpenAPI file validates against OpenAPI 3.1 schema (use Swagger Editor or spectral)\n        *   All CRUD endpoints for core entities documented\n        *   Security requirements specified for protected endpoints\n        *   DTO schemas match database entity structure (field names, types, nullability)\n        *   Error responses follow consistent structure across all endpoints\n        *   File imports successfully into Swagger UI or Redoc for documentation rendering",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: API Design Overview (from docs/api-design.md)

```markdown
# Planning Poker API Design

## Overview

The Planning Poker API is a RESTful JSON API following OpenAPI 3.1 specification. It provides endpoints for user authentication, room management, subscription billing, reporting, and enterprise organization management.

**OpenAPI Specification:** [`/api/openapi.yaml`](../api/openapi.yaml)

**Base URL:** `https://api.planningpoker.example.com`

**API Version:** v1 (all endpoints use `/api/v1/` prefix)
...
### Error Handling

All error responses follow a consistent structure:

```
{
  "error": "ERROR_CODE",
  "message": "Human-readable error description",
  "timestamp": "2025-01-15T10:30:00Z",
  "details": {}
}
```
```

### Context: Authentication & User Management (from docs/api-design.md)

```markdown
### 1. Authentication (`/api/v1/auth/*`)

OAuth2 authentication with Google and Microsoft providers.

**Endpoints:**
- `POST /api/v1/auth/oauth/callback` - Exchange OAuth code for tokens
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/logout` - Revoke refresh token

### 2. User Management (`/api/v1/users/*`)

User profile and preference management.

**Endpoints:**
- `GET /api/v1/users/{userId}` - Get user profile
- `PUT /api/v1/users/{userId}` - Update profile (display name, avatar)
- `GET /api/v1/users/{userId}/preferences` - Get preferences
- `PUT /api/v1/users/{userId}/preferences` - Update preferences (theme, default room config)

**Permissions:** Users can only modify their own profile and preferences.
```

### Context: Room Management Requirements (from docs/api-design.md)

```markdown
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
```

### Context: Subscription & Reporting Domains (from docs/api-design.md)

```markdown
### 4. Subscription & Billing (`/api/v1/subscriptions/*`)

Stripe integration for subscription management.

**Tiers:** Free, Pro, Pro Plus, Enterprise with increasing limits.

**Endpoints:**
- `GET /api/v1/subscriptions/{userId}` - Get subscription status
- `POST /api/v1/subscriptions/checkout` - Create Stripe checkout session
- `POST /api/v1/subscriptions/{subscriptionId}/cancel` - Cancel subscription
- `POST /api/v1/subscriptions/webhook` - Stripe webhook handler
- `GET /api/v1/billing/invoices` - List payment history

### 5. Reporting & Analytics (`/api/v1/reports/*`)

Session history, detailed reports, and export jobs with tier restrictions.

**Endpoints:**
- `GET /api/v1/reports/sessions` - List session history
- `GET /api/v1/reports/sessions/{sessionId}` - Detailed session report
- `POST /api/v1/reports/export` - Create export job (CSV/PDF)
- `GET /api/v1/jobs/{jobId}` - Poll export job status
```

### Context: Enterprise Organization Requirements (from docs/api-design.md)

```markdown
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
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `api/openapi.yaml`
    *   **Summary:** The repo already contains a comprehensive OpenAPI 3.1 document covering authentication, user, room, subscription, reporting, and organization domains plus shared components (parameters, responses, and DTO-style schemas). It establishes reusable error responses, enums (tiers, privacy modes, roles), pagination contracts, and detailed examples for every endpoint.
    *   **Recommendation:** When extending or refining the spec, preserve the existing structure: group endpoints by tag, keep verbose `description` fields, link responses to shared components, and update schemas/enum lists in one place to avoid drift with backend DTOs.
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** Implements the `/api/v1/rooms` family today, relying on `RoomService` and `RoomMapper`, with annotations documenting expected behavior (permit anonymous creation, `RolesAllowed` for updates/deletes, pagination query params). Comments reference the OpenAPI contract and note upcoming auth enforcement points.
    *   **Recommendation:** Mirror these real endpoints when documenting path/operation details—reuse request/response DTO names from this controller, and ensure parameter descriptions (roomId format, pagination limits) stay synchronized with controller validations.
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/UserController.java`
    *   **Summary:** Provides the `/api/v1/users/{userId}` and preferences operations with `UserService` + `UserMapper`. It emphasises ownership restrictions, enumerates response codes, and references DTOs already in `api/rest/dto`.
    *   **Recommendation:** Use the DTO fields defined here (e.g., `UpdateProfileRequest`, `UserPreferenceDTO`) when shaping schemas. Align the OpenAPI descriptions with controller comments so future changes to service expectations require updates in one place.

### Implementation Tips & Notes
*   **Tip:** Follow the pattern from the current spec—each path starts with `summary`, `description`, `operationId`, `tags`, and enumerates both success and common error responses referencing `#/components/responses/*`.
*   **Tip:** Schemas are centralized; if new DTOs/entities emerge, add them under `components.schemas` with clear `required` arrays, max lengths, formats, and sample payloads. Reference them from both requests and responses to avoid duplication.
*   **Tip:** Maintain security definitions at both global (`security:` block) and per-operation levels. Public endpoints explicitly clear security with `security: []`, while protected operations inherit the top-level Bearer requirement.
*   **Tip:** Keep validation/ref constraint parity with the backend—match UUID formats, `^[a-z0-9]{6}$` room ID pattern, enum values (e.g., `PrivacyMode`, `SubscriptionTier`), and include pagination parameter definitions from the shared `components.parameters` section.
*   **Tip:** Before handing off, run `spectral lint api/openapi.yaml` (per docs/api-design.md) or import into Swagger Editor to ensure the YAML remains valid and human-readable.
