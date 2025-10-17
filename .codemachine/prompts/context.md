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
  "inputs": "REST API endpoint overview from architecture blueprint (Section 4 - API Design), Entity models from I1.T4 (for DTO schema definitions), Authentication/authorization requirements",
  "input_files": [
    ".codemachine/artifacts/architecture/04_Behavior_and_Communication.md",
    "backend/src/main/java/com/scrumpoker/domain/**/*.java"
  ],
  "target_files": [
    "api/openapi.yaml",
    "docs/api-design.md"
  ],
  "deliverables": "OpenAPI 3.1 YAML file with 30+ endpoint definitions, Complete schema definitions for all DTOs (User, Room, Vote, Subscription, Organization, etc.), Error response schema with standardized structure (`{\"error\": \"...\", \"message\": \"...\", \"timestamp\": \"...\"}`), Security scheme definitions (JWT Bearer, OAuth2 authorization code flow), Request/response examples for critical endpoints, Validation rules in schemas (string formats, numeric ranges, enum values)",
  "acceptance_criteria": "OpenAPI file validates against OpenAPI 3.1 schema (use Swagger Editor or spectral), All CRUD endpoints for core entities documented, Security requirements specified for protected endpoints, DTO schemas match database entity structure (field names, types, nullability), Error responses follow consistent structure across all endpoints, File imports successfully into Swagger UI or Redoc for documentation rendering",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: api-design-and-communication (from 04_Behavior_and_Communication.md)

```markdown
<!-- anchor: api-design-and-communication -->
### 3.7. API Design & Communication

<!-- anchor: api-style -->
#### API Style

**Primary API Style:** **RESTful JSON API (OpenAPI 3.1 Specification)**

**Rationale:**
- **Simplicity & Familiarity:** REST over HTTPS provides a well-understood contract for CRUD operations on resources (users, rooms, subscriptions)
- **Tooling Ecosystem:** OpenAPI specification enables automatic client SDK generation (TypeScript for React frontend), API documentation (Swagger UI), and contract testing
- **Caching Support:** HTTP semantics (ETags, Cache-Control headers) enable browser and CDN caching for read-heavy endpoints (room configurations, user profiles)
- **Versioning Strategy:** URL-based versioning (`/api/v1/`) for backward compatibility during iterative releases

**WebSocket Protocol:** **Custom JSON-RPC Style Over WebSocket**

**Rationale:**
- **Real-Time Bidirectional Communication:** WebSocket connections maintained for duration of estimation session, enabling sub-100ms latency for vote events and reveals
- **Message Format:** JSON envelopes with `type`, `requestId`, and `payload` fields for request/response correlation
- **Versioned Message Types:** Each message type (e.g., `vote.cast.v1`, `room.reveal.v1`) versioned independently for protocol evolution
- **Fallback Strategy:** Graceful degradation to HTTP long-polling for environments with WebSocket restrictions (corporate proxies)

**Alternative Considered:**
- **GraphQL:** Rejected due to complexity overhead for small team and straightforward data model. GraphQL subscription complexity for WebSocket integration not justified by query flexibility benefits.
- **gRPC:** Rejected due to browser support limitations (requires gRPC-Web proxy) and team unfamiliarity. Better suited for backend-to-backend microservice communication.
```

### Context: rest-api-endpoints (from 04_Behavior_and_Communication.md)

```markdown
<!-- anchor: rest-api-endpoints -->
#### REST API Endpoints Overview

**Authentication & User Management:**
- `POST /api/v1/auth/oauth/callback` - Exchange OAuth2 code for JWT tokens
- `POST /api/v1/auth/refresh` - Refresh expired access token
- `POST /api/v1/auth/logout` - Revoke refresh token
- `GET /api/v1/users/{userId}` - Retrieve user profile
- `PUT /api/v1/users/{userId}` - Update profile (display name, avatar)
- `GET /api/v1/users/{userId}/preferences` - Get user preferences
- `PUT /api/v1/users/{userId}/preferences` - Update default room settings, theme

**Room Management:**
- `POST /api/v1/rooms` - Create new room (authenticated or anonymous)
- `GET /api/v1/rooms/{roomId}` - Get room configuration and current state
- `PUT /api/v1/rooms/{roomId}/config` - Update room settings (host only)
- `DELETE /api/v1/rooms/{roomId}` - Delete room (owner only)
- `GET /api/v1/users/{userId}/rooms` - List user's owned rooms

**Subscription & Billing:**
- `GET /api/v1/subscriptions/{userId}` - Get current subscription status
- `POST /api/v1/subscriptions/checkout` - Create Stripe checkout session for upgrade
- `POST /api/v1/subscriptions/{subscriptionId}/cancel` - Cancel subscription (end of period)
- `POST /api/v1/subscriptions/webhook` - Stripe webhook endpoint (signature verification)
- `GET /api/v1/billing/invoices` - List payment history

**Reporting & Analytics:**
- `GET /api/v1/reports/sessions` - List session history (tier-gated pagination, filters)
- `GET /api/v1/reports/sessions/{sessionId}` - Detailed session report (tier-gated round detail)
- `POST /api/v1/reports/export` - Generate export job (CSV/PDF), returns job ID
- `GET /api/v1/jobs/{jobId}` - Poll export job status, retrieve download URL

**Organization Management (Enterprise):**
- `POST /api/v1/organizations` - Create organization workspace
- `GET /api/v1/organizations/{orgId}` - Get org settings
- `PUT /api/v1/organizations/{orgId}/sso` - Configure OIDC/SAML2 settings
- `POST /api/v1/organizations/{orgId}/members` - Invite member
- `DELETE /api/v1/organizations/{orgId}/members/{userId}` - Remove member
- `GET /api/v1/organizations/{orgId}/audit-logs` - Query audit trail
```

### Context: synchronous-rest-pattern (from 04_Behavior_and_Communication.md)

```markdown
<!-- anchor: synchronous-rest-pattern -->
##### Synchronous REST (Request/Response)

**Use Cases:**
- User authentication and registration
- Room creation and configuration updates
- Subscription management (upgrade, cancellation, payment method updates)
- Report generation triggers and export downloads
- Organization settings management

**Pattern Characteristics:**
- Client blocks waiting for server response (typically <500ms)
- Transactional consistency guaranteed within single database transaction
- Idempotency keys for payment operations to prevent duplicate charges
- Error responses use standard HTTP status codes (4xx client errors, 5xx server errors)

**Example Endpoints:**
- `POST /api/v1/auth/oauth/callback` - Exchange OAuth2 code for JWT token
- `POST /api/v1/rooms` - Create new estimation room
- `GET /api/v1/rooms/{roomId}` - Retrieve room configuration
- `PUT /api/v1/users/{userId}/preferences` - Update user preferences
- `POST /api/v1/subscriptions/{subscriptionId}/upgrade` - Upgrade subscription tier
- `GET /api/v1/reports/sessions?from=2025-01-01&to=2025-01-31` - Query session history
```

### Context: data-model-overview-erd (from 03_System_Structure_and_Data.md)

```markdown
<!-- anchor: data-model-overview-erd -->
### 3.6. Data Model Overview & ERD

#### Description

The data model follows a relational schema leveraging PostgreSQL's ACID properties for transactional consistency and JSONB columns for flexible configuration storage (room settings, deck definitions). The model is optimized for both transactional writes (vote casting, room creation) and analytical reads (session history, organizational reporting).

**Design Principles:**
1. **Normalized Core Entities:** Users, Rooms, Organizations follow 3NF to prevent update anomalies
2. **Denormalized Read Models:** SessionSummary and VoteStatistics tables precompute aggregations for reporting performance
3. **JSONB for Flexibility:** RoomConfig, DeckDefinition, UserPreferences stored as JSONB to support customization without schema migrations
4. **Soft Deletes:** Critical entities (Users, Rooms) use `deleted_at` timestamp for audit trail and GDPR compliance
5. **Partitioning Strategy:** SessionHistory and AuditLog partitioned by month for query performance and data lifecycle management

#### Key Entities

| Entity | Purpose | Key Attributes |
|--------|---------|----------------|
| **User** | Registered user account | `user_id` (PK), `email`, `oauth_provider`, `oauth_subject`, `display_name`, `avatar_url`, `subscription_tier`, `created_at` |
| **UserPreference** | Saved user defaults | `user_id` (FK), `default_deck_type`, `default_room_config` (JSONB), `theme`, `notification_settings` (JSONB) |
| **Organization** | Enterprise SSO workspace | `org_id` (PK), `name`, `domain`, `sso_config` (JSONB: OIDC/SAML2 settings), `branding` (JSONB), `subscription_id` (FK) |
| **OrgMember** | User-organization membership | `org_id` (FK), `user_id` (FK), `role` (ADMIN/MEMBER), `joined_at` |
| **Room** | Estimation session | `room_id` (PK, nanoid 6-char), `owner_id` (FK nullable for anonymous), `org_id` (FK nullable), `title`, `privacy_mode` (PUBLIC/INVITE_ONLY/ORG_RESTRICTED), `config` (JSONB: deck, rules, timer), `created_at`, `last_active_at` |
| **RoomParticipant** | Active session participants | `room_id` (FK), `user_id` (FK nullable), `anonymous_id`, `display_name`, `role` (HOST/VOTER/OBSERVER), `connected_at` |
| **Vote** | Individual estimation vote | `vote_id` (PK), `room_id` (FK), `round_number`, `participant_id`, `card_value`, `voted_at` |
| **Round** | Estimation round within session | `round_id` (PK), `room_id` (FK), `round_number`, `story_title`, `started_at`, `revealed_at`, `average`, `median`, `consensus_reached` |
| **SessionHistory** | Completed session record | `session_id` (PK), `room_id` (FK), `started_at`, `ended_at`, `total_rounds`, `total_stories`, `participants` (JSONB array), `summary_stats` (JSONB) |
| **Subscription** | Stripe subscription record | `subscription_id` (PK), `stripe_subscription_id`, `entity_id` (user_id or org_id), `entity_type` (USER/ORG), `tier` (FREE/PRO/PRO_PLUS/ENTERPRISE), `status`, `current_period_end`, `canceled_at` |
| **PaymentHistory** | Payment transaction log | `payment_id` (PK), `subscription_id` (FK), `stripe_invoice_id`, `amount`, `currency`, `status`, `paid_at` |
| **AuditLog** | Compliance and security audit trail | `log_id` (PK), `org_id` (FK nullable), `user_id` (FK nullable), `action`, `resource_type`, `resource_id`, `ip_address`, `user_agent`, `timestamp` |
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** This file defines the `User` entity with OAuth authentication fields. Key fields include: `userId` (UUID), `email`, `oauthProvider`, `oauthSubject`, `displayName`, `avatarUrl`, `subscriptionTier` (enum), `createdAt`, `updatedAt`, and `deletedAt` (for soft deletes). The entity supports unique constraints on email and oauth_provider+oauth_subject combination.
    *   **Recommendation:** When creating the UserDTO schema in OpenAPI, you MUST map all these fields correctly. Note that `deletedAt` should NOT be exposed in the DTO for privacy reasons. The `subscriptionTier` field uses the `SubscriptionTier` enum which you can find in `backend/src/main/java/com/scrumpoker/domain/user/SubscriptionTier.java`. Your UserDTO schema MUST include: userId (UUID format), email (email format), displayName (string, max 100 chars), avatarUrl (string, max 500 chars, nullable), subscriptionTier (enum: FREE, PRO, PRO_PLUS, ENTERPRISE).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
    *   **Summary:** This file defines the `Room` entity representing estimation sessions. Critical fields: `roomId` (String, 6-character nanoid - NOT a UUID!), `owner` (ManyToOne to User, nullable for anonymous rooms), `organization` (ManyToOne to Organization, nullable), `title` (String, max 255), `privacyMode` (enum), `config` (String containing JSONB), `createdAt`, `lastActiveAt`, `deletedAt` (soft delete).
    *   **Recommendation:** Your RoomDTO schema MUST reflect that `roomId` is a **6-character string**, not a UUID. The `config` field is stored as JSONB and should be represented as an object in the OpenAPI schema with properties like: `deckType`, `timerEnabled`, `timerDurationSeconds`, `revealBehavior`, `allowObservers`. The `privacyMode` enum values are defined in `backend/src/main/java/com/scrumpoker/domain/room/PrivacyMode.java` (PUBLIC, INVITE_ONLY, ORG_RESTRICTED).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/Subscription.java`
    *   **Summary:** Defines the `Subscription` entity linking users/organizations to Stripe subscriptions. Key fields: `subscriptionId` (UUID), `stripeSubscriptionId` (String, max 100), `entityId` (UUID - polymorphic reference to User or Organization), `entityType` (enum: USER, ORG), `tier` (SubscriptionTier enum), `status` (SubscriptionStatus enum), `currentPeriodStart`, `currentPeriodEnd`, `canceledAt`.
    *   **Recommendation:** Your SubscriptionDTO schema MUST include all these fields with proper types. The `entityType` field is an enum defined in `backend/src/main/java/com/scrumpoker/domain/billing/EntityType.java`. The `status` field uses the `SubscriptionStatus` enum found in `backend/src/main/java/com/scrumpoker/domain/billing/SubscriptionStatus.java` with values: ACTIVE, PAST_DUE, CANCELED, TRIALING.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/Organization.java`
    *   **Summary:** Defines the `Organization` entity for Enterprise customers. Fields: `orgId` (UUID), `name` (String, max 255), `domain` (String, max 255, unique), `ssoConfig` (String containing JSONB), `branding` (String containing JSONB), `subscription` (ManyToOne to Subscription), `createdAt`, `updatedAt`.
    *   **Recommendation:** Your OrganizationDTO schema should represent `ssoConfig` and `branding` as objects. The `ssoConfig` JSONB should contain fields for OIDC/SAML2 configuration. The `branding` JSONB should include: `logoUrl`, `primaryColor`, `secondaryColor`.

*   **File:** `backend/src/main/resources/db/migration/V1__initial_schema.sql`
    *   **Summary:** This migration script creates all 11 core database tables and defines ENUM types. It provides authoritative information on column types, constraints, and nullability.
    *   **Recommendation:** Use this file as the **source of truth** for field constraints in your OpenAPI schemas. For example:
        - User.email: VARCHAR(255), NOT NULL, UNIQUE → OpenAPI: type: string, format: email, maxLength: 255, required: true
        - Room.roomId: VARCHAR(6), CHECK (LENGTH(room_id) = 6) → OpenAPI: type: string, pattern: "^[a-z0-9]{6}$", minLength: 6, maxLength: 6
        - Vote.card_value: VARCHAR(10) → OpenAPI: type: string, maxLength: 10

### Implementation Tips & Notes

*   **Tip:** The project uses ENUMs extensively for type safety. You MUST define all enum values in the OpenAPI schema using the `enum` keyword. I have identified the following enums in the codebase that you MUST document:
    - `SubscriptionTier`: FREE, PRO, PRO_PLUS, ENTERPRISE (from V1 migration)
    - `SubscriptionStatus`: ACTIVE, PAST_DUE, CANCELED, TRIALING (from V1 migration)
    - `PrivacyMode`: PUBLIC, INVITE_ONLY, ORG_RESTRICTED (from V1 migration)
    - `RoomRole`: HOST, VOTER, OBSERVER (from V1 migration)
    - `OrgRole`: ADMIN, MEMBER (from V1 migration)
    - `EntityType`: USER, ORG (from V1 migration)
    - `PaymentStatus`: SUCCEEDED, PENDING, FAILED (from V1 migration)

*   **Tip:** JSONB fields in PostgreSQL are stored as strings in the Java entities (e.g., `Room.config` is `public String config`). In your OpenAPI schema, you should represent these as **objects** with defined properties for better API documentation and client SDK generation. For example, the Room config JSONB should be documented as an object with properties: deckType, timerEnabled, timerDurationSeconds, revealBehavior, allowObservers.

*   **Note:** The architecture specifies OAuth2 authorization code flow for authentication. Your OpenAPI security schemes MUST include:
    1. **BearerAuth** (HTTP Bearer scheme) for JWT token-based authentication on most endpoints
    2. **OAuth2** security scheme with authorization code flow for the `/api/v1/auth/oauth/callback` endpoint

*   **Note:** The architecture blueprint specifies URL-based versioning with `/api/v1/` prefix. ALL endpoint paths in your OpenAPI spec MUST begin with `/api/v1/`.

*   **Note:** The acceptance criteria requires a standardized error response schema. You MUST define a reusable `ErrorResponse` component in your OpenAPI spec with the structure: `{ "error": "string", "message": "string", "timestamp": "string (ISO 8601 datetime)" }`. Reference this schema in ALL error responses (400, 401, 403, 404, 500).

*   **Warning:** The Room entity has a special `roomId` field that is a **6-character nanoid string**, NOT a UUID like other entities. This is critical for URL sharing (`/room/abc123`). Ensure your OpenAPI path parameter definition for `{roomId}` reflects this with proper pattern validation.

*   **Warning:** Several entities support soft deletes (User, Room) via a `deleted_at` column. DO NOT expose the `deleted_at` field in your DTO schemas - it's an internal implementation detail. Deleted entities should simply not be returned by the API.

*   **Tip:** The architecture mentions "tier-gated" features extensively. While you're only documenting the API in this task (not implementing enforcement), you should add clear descriptions to endpoints that have tier restrictions. For example, the detailed session report endpoint should have a description noting: "Requires Pro tier or higher. Free tier users receive a 403 Forbidden response."

*   **Tip:** For request/response examples in your OpenAPI spec, use realistic sample data that reflects the actual domain. For example, a Room example should use a 6-character roomId like "abc123", not a UUID. A User example should include a valid email and realistic OAuth provider (google/microsoft).

*   **Note:** The project structure shows that the `api/` directory does not yet exist. You will need to create it when writing `api/openapi.yaml`.

*   **Note:** The architecture specifies that WebSocket authentication uses JWT tokens passed as query parameters (`?token={jwt}`). While WebSocket protocol is documented separately (task I2.T2), your OpenAPI spec should document this pattern in the description of authentication endpoints that issue these tokens.

*   **Tip:** For the `docs/api-design.md` file, create a concise markdown document that provides an overview of the API design philosophy, links to the OpenAPI specification, and quick reference for developers. Include sections on: Authentication flow, API versioning strategy, Error handling, Rate limiting (if applicable), and how to use the Swagger UI for testing.

*   **CRITICAL:** You MUST validate your OpenAPI file against the OpenAPI 3.1 schema before completing this task. Use online tools like Swagger Editor (https://editor.swagger.io/) or the `spectral` CLI tool. The acceptance criteria explicitly requires this validation to pass.

*   **CRITICAL:** Ensure your schemas match the database entity structure exactly. Field names should use camelCase in JSON (matching Java conventions), types should align with database column types, and nullability should match the database schema. Any mismatch will cause issues in future iterations when services and controllers are implemented.
