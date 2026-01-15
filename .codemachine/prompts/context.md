# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T6",
  "iteration_id": "I3",
  "iteration_goal": "Implement OAuth2 authentication (Google, Microsoft), JWT token generation/validation, user registration/login flows, and frontend authentication UI to enable secured access to the application.",
  "description": "Create API client wrapper using React Query integrating authentication. Configure Axios instance with base URL, request interceptor to add `Authorization: Bearer <token>` header from authStore, response interceptor to handle 401 errors (refresh token or logout). Implement token refresh logic: on 401, call `/api/v1/auth/refresh`, update tokens in store, retry original request. Create React Query hooks for common API calls: `useUser(userId)`, `useRooms()`, `useRoomById(roomId)`. Handle loading and error states.",
  "agent_type_hint": "FrontendAgent",
  "inputs": "*   OpenAPI spec for endpoint definitions\n        *   React Query patterns\n        *   Token refresh flow requirements",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   Axios instance configured with baseURL, timeout\n        *   Request interceptor adding Authorization header from authStore\n        *   Response interceptor detecting 401, triggering token refresh\n        *   Token refresh logic: call /refresh API, update authStore, retry request\n        *   React Query hooks: useUser, useRooms, useRoomById\n        *   Error handling: network errors, 500 server errors",
  "acceptance_criteria": "*   API requests include Authorization header when user authenticated\n        *   Expired access token triggers refresh automatically\n        *   After refresh, original request retries successfully\n        *   If refresh fails (invalid refresh token), user logged out and redirected to login\n        *   React Query hooks return loading/error/data states correctly\n        *   Cache invalidation works (e.g., after room creation, useRooms refetches)",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Task 3.6 – Implement Frontend API Client with Authentication (from .codemachine/artifacts/plan/02_Iteration_I3.md)

```markdown
*   **Task 3.6: Implement Frontend API Client with Authentication**
    *   **Task ID:** `I3.T6`
    *   **Description:** Create API client wrapper using React Query integrating authentication. Configure Axios instance with base URL, request interceptor to add `Authorization: Bearer <token>` header from authStore, response interceptor to handle 401 errors (refresh token or logout). Implement token refresh logic: on 401, call `/api/v1/auth/refresh`, update tokens in store, retry original request. Create React Query hooks for common API calls: `useUser(userId)`, `useRooms()`, `useRoomById(roomId)`. Handle loading and error states.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   OpenAPI spec for endpoint definitions
        *   React Query patterns
        *   Token refresh flow requirements
    *   **Input Files:**
        *   `api/openapi.yaml`
        *   `frontend/src/stores/authStore.ts`
    *   **Target Files:**
        *   `frontend/src/services/api.ts` (Axios instance with interceptors)
        *   `frontend/src/services/apiHooks.ts` (React Query hooks)
        *   `frontend/src/services/authApi.ts` (auth-specific API calls)
    *   **Deliverables:**
        *   Axios instance configured with baseURL, timeout
        *   Request interceptor adding Authorization header from authStore
        *   Response interceptor detecting 401, triggering token refresh
        *   Token refresh logic: call /refresh API, update authStore, retry request
        *   React Query hooks: useUser, useRooms, useRoomById
        *   Error handling: network errors, 500 server errors
    *   **Acceptance Criteria:**
        *   API requests include Authorization header when user authenticated
        *   Expired access token triggers refresh automatically
        *   After refresh, original request retries successfully
        *   If refresh fails (invalid refresh token), user logged out and redirected to login
        *   React Query hooks return loading/error/data states correctly
        *   Cache invalidation works (e.g., after room creation, useRooms refetches)
    *   **Dependencies:** [I3.T5]
    *   **Parallelizable:** No (depends on authStore)
```

### Context: API Style (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

```markdown
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

---
```

### Context: Authentication Mechanisms (from .codemachine/artifacts/architecture/05_Operational_Architecture.md)

```markdown
##### Authentication Mechanisms

**OAuth2 Social Login (Free/Pro Tiers):**
- **Providers:** Google OAuth2, Microsoft Identity Platform
- **Flow:** Authorization Code Flow with PKCE (Proof Key for Code Exchange) for browser-based clients
- **Implementation:** Quarkus OIDC extension handling token exchange and validation
- **Token Storage:** JWT access tokens (1-hour expiration) in browser `localStorage`, refresh tokens (30-day expiration) in `httpOnly` secure cookies
- **User Provisioning:** Automatic user creation on first login with `oauth_provider` and `oauth_subject` as unique identifiers
- **Profile Sync:** Email, display name, and avatar URL synced from OAuth provider on each login

**Enterprise SSO (Enterprise Tier):**
- **Protocols:** OIDC (OpenID Connect) and SAML2 support via Quarkus Security extensions
- **Configuration:** Per-organization SSO settings stored in `Organization.sso_config` JSONB field (IdP endpoint, certificate, attribute mapping)
- **Domain Enforcement:** Email domain verification ensures users with `@company.com` email automatically join organization workspace
- **Just-In-Time (JIT) Provisioning:** User accounts created on first SSO login with organization membership pre-assigned
- **Session Management:** SSO sessions synchronized with IdP via backchannel logout or session validation

**Anonymous Play:**
- **Identifier:** Client-generated UUID stored in browser `sessionStorage` for session continuity
- **Room Association:** Anonymous participants linked to room via `RoomParticipant.anonymous_id`
- **Feature Restrictions:** No session history access, no saved preferences, no administrative capabilities
- **Data Lifecycle:** Anonymous session data purged 24 hours after room inactivity
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `frontend/src/services/api.ts`
    *   **Summary:** Configures the shared Axios instance with the API base URL, JSON headers, and a 10s timeout, injects the bearer token from `useAuthStore`, handles a feature-gate specific 403 hook, and owns the refresh queue that serializes `/auth/refresh` calls, updates the store, and retries failed requests.
    *   **Recommendation:** Reuse this client for every authenticated HTTP call so interceptors remain centralized. When adding new services, never bypass `apiClient`—it already raises the Upgrade modal via `registerFeatureNotAvailableHandler` and gracefully clears auth on refresh failure.
*   **File:** `frontend/src/services/authApi.ts`
    *   **Summary:** Provides the stand-alone Axios instance reserved for auth flows, specifically `refreshAccessToken` (POST `/auth/refresh`) and `logout`, so that those calls are not intercepted and cannot loop endlessly.
    *   **Recommendation:** Always import `refreshAccessToken` from here when touching the response interceptor or future auth utilities. If you add more auth-only calls, keep them in this module to avoid polluting the standard client.
*   **File:** `frontend/src/services/apiHooks.ts`
    *   **Summary:** Supplies the React Query integration points, including the canonical `queryKeys`, `useUser`, `useRooms`, `useRoomById`, and several room mutations that invalidate caches and surface typed errors via `getErrorMessage`.
    *   **Recommendation:** Follow the existing query key factories when introducing new hooks so invalidation stays consistent. Remember to scope hooks like `useRooms` to the authenticated user ID (pulled from `useAuthStore`) and set meaningful `staleTime` values per endpoint volatility.
*   **File:** `frontend/src/stores/authStore.ts`
    *   **Summary:** Zustand store that hydrates from `localStorage`, tracks `{user, accessToken, refreshToken, isAuthenticated}`, and offers `setAuth`, `clearAuth`, and `loadAuthFromStorage` helpers with built-in persistence/cleanup logic.
    *   **Recommendation:** Whenever you receive a `TokenResponse`, call `setAuth` rather than writing to storage manually. Use `clearAuth` after logout/refresh failure so state, storage, and derived boolean stay in sync.

### Implementation Tips & Notes
*   **Tip:** `apiClient` already exposes `getErrorMessage` and the feature gate handler; wire UI-level upgrade prompts by calling `registerFeatureNotAvailableHandler` once in your app shell rather than sprinkling modal logic inside each hook.
*   **Tip:** Refresh queuing relies on `_retry` flagging per request—if you manually trigger retries (e.g., via React Query `retry`), be sure they don’t re-add `_retry` or you may skip the refresh flow entirely.
*   **Tip:** React Query keys defined in `queryKeys` should be reused across components and mutations so cache invalidation functions (`invalidateQueries`, `setQueryData`, `removeQueries`) keep working without duplicated strings.
*   **Note:** `refreshAccessToken` expects the raw refresh token string and returns the full `TokenResponse` (including the newly rotated refresh token). Always propagate the entire response to `setAuth` so old tokens don’t linger in storage.
*   **Note:** Error surfaces in hooks currently throw `Error` objects (rather than Axios responses). Wrap network-level issues using `getErrorMessage(error)` before logging or alerting so downstream UI gets consistent strings.
