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
  "inputs": "OpenAPI spec for endpoint definitions, React Query patterns, Token refresh flow requirements",
  "input_files": [
    "api/openapi.yaml",
    "frontend/src/stores/authStore.ts"
  ],
  "target_files": [
    "frontend/src/services/api.ts",
    "frontend/src/services/apiHooks.ts",
    "frontend/src/services/authApi.ts"
  ],
  "deliverables": "Axios instance configured with baseURL, timeout, Request interceptor adding Authorization header from authStore, Response interceptor detecting 401, triggering token refresh, Token refresh logic: call /refresh API, update authStore, retry request, React Query hooks: useUser, useRooms, useRoomById, Error handling: network errors, 500 server errors",
  "acceptance_criteria": "API requests include Authorization header when user authenticated, Expired access token triggers refresh automatically, After refresh, original request retries successfully, If refresh fails (invalid refresh token), user logged out and redirected to login, React Query hooks return loading/error/data states correctly, Cache invalidation works (e.g., after room creation, useRooms refetches)",
  "dependencies": [
    "I3.T5"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: rest-api-endpoints (from 04_Behavior_and_Communication.md)

```markdown
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

### Context: authentication-security (from 05_Operational_Architecture.md)

```markdown
**Authentication Security:**
- **JWT Signature:** RS256 (RSA with SHA-256) algorithm, private key stored in Kubernetes Secret
- **Token Expiration:** Short-lived access tokens (1 hour), refresh tokens rotated on use
- **OAuth2 State Parameter:** CSRF protection for OAuth flow, state validated on callback
- **PKCE:** Protects authorization code from interception in browser-based flows
```

### Context: authentication-mechanisms (from 05_Operational_Architecture.md)

```markdown
**OAuth2 Social Login (Free/Pro Tiers):**
- **Providers:** Google OAuth2, Microsoft Identity Platform
- **Flow:** Authorization Code Flow with PKCE (Proof Key for Code Exchange) for browser-based clients
- **Implementation:** Quarkus OIDC extension handling token exchange and validation
- **Token Storage:** JWT access tokens (1-hour expiration) in browser `localStorage`, refresh tokens (30-day expiration) in `httpOnly` secure cookies
- **User Provisioning:** Automatic user creation on first login with `oauth_provider` and `oauth_subject` as unique identifiers
- **Profile Sync:** Email, display name, and avatar URL synced from OAuth provider on each login
```

### Context: api-style (from 04_Behavior_and_Communication.md)

```markdown
**Primary API Style:** **RESTful JSON API (OpenAPI 3.1 Specification)**

**Rationale:**
- **Simplicity & Familiarity:** REST over HTTPS provides a well-understood contract for CRUD operations on resources (users, rooms, subscriptions)
- **Tooling Ecosystem:** OpenAPI specification enables automatic client SDK generation (TypeScript for React frontend), API documentation (Swagger UI), and contract testing
- **Caching Support:** HTTP semantics (ETags, Cache-Control headers) enable browser and CDN caching for read-heavy endpoints (room configurations, user profiles)
- **Versioning Strategy:** URL-based versioning (`/api/v1/`) for backward compatibility during iterative releases
```

### Context: synchronous-rest-pattern (from 04_Behavior_and_Communication.md)

```markdown
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
```

### Context: Refresh Token Endpoint (from openapi.yaml)

```yaml
/api/v1/auth/refresh:
  post:
    tags:
      - Authentication
    summary: Refresh expired access token
    description: |
      Exchanges refresh token for new access token. Refresh tokens are single-use and rotated on each refresh.
    operationId: refreshToken
    security: []  # Uses refresh token, not access token
    requestBody:
      required: true
      content:
        application/json:
          schema:
            type: object
            required:
              - refreshToken
            properties:
              refreshToken:
                type: string
                description: Valid refresh token
                example: "v1.MR5tqKz..."
    responses:
      '200':
        description: Token refreshed successfully
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/TokenResponse'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '500':
        $ref: '#/components/responses/InternalServerError'
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `frontend/src/stores/authStore.ts`
    *   **Summary:** This file contains the Zustand authentication store that manages user authentication state, access tokens, refresh tokens, and localStorage persistence. It exposes three key actions: `setAuth()` (stores tokens and user data), `clearAuth()` (logs user out), and `loadAuthFromStorage()` (restores session from localStorage on app reload).
    *   **Recommendation:** You MUST import and use the `useAuthStore` hook from this file in your API client. The store provides the `accessToken` that should be attached to the `Authorization` header. When a token refresh succeeds, you MUST call `setAuth(tokenResponse)` to update the store. When a refresh fails (indicating the user's session is invalid), you MUST call `clearAuth()` to log them out.

*   **File:** `frontend/src/hooks/useAuth.ts`
    *   **Summary:** This is a convenience hook that provides access to the authentication store's state and actions. It exports: `user`, `accessToken`, `refreshToken`, `isAuthenticated`, `setAuth`, `clearAuth`, and `loadAuthFromStorage`.
    *   **Recommendation:** You SHOULD NOT directly use this hook in the API client layer (services/api.ts) because React hooks cannot be called outside React components. Instead, directly import `useAuthStore` from the store file and use `.getState()` to access the state imperatively. However, you WILL use this hook in your React Query hooks (apiHooks.ts) to access authentication state within React components.

*   **File:** `frontend/src/types/auth.ts`
    *   **Summary:** This file defines TypeScript interfaces matching the OpenAPI specification, including: `UserDTO`, `TokenResponse`, `OAuthCallbackRequest`, and `ErrorResponse`. These types ensure type safety for API requests and responses.
    *   **Recommendation:** You MUST import and use the `TokenResponse` type for the refresh endpoint response. You MUST import and use the `UserDTO` type for user-related API responses. You SHOULD reuse the `ErrorResponse` type for error handling logic.

*   **File:** `api/openapi.yaml`
    *   **Summary:** This is the comprehensive OpenAPI 3.1 specification defining all REST API endpoints, request/response schemas, authentication requirements, and error codes. Key endpoints for this task: `/api/v1/auth/refresh` (POST, refreshes access token), `/api/v1/users/{userId}` (GET, retrieves user profile), `/api/v1/rooms` (POST, creates room; GET list via `/api/v1/users/{userId}/rooms`), `/api/v1/rooms/{roomId}` (GET, retrieves room details).
    *   **Recommendation:** You MUST reference this specification when implementing API calls. All request payloads and response structures MUST match the defined schemas. Error handling MUST align with the documented error codes (400, 401, 403, 404, 500).

*   **File:** `frontend/vite.config.ts`
    *   **Summary:** This configuration file defines path aliases for the project (e.g., `@/services`, `@/stores`, `@/types`) and sets up a proxy for API calls during development (`/api` proxies to `http://localhost:8080`).
    *   **Recommendation:** You MUST use the base URL `/api/v1` for your Axios instance (NOT the full `http://localhost:8080`), as the Vite dev server proxy will handle routing during development. In production, the base URL should be configurable via environment variable.

*   **File:** `frontend/package.json`
    *   **Summary:** The project has `@tanstack/react-query` version `^5.12.0` installed for data fetching and caching. It also has `zustand` for state management, and `zod` for schema validation. Axios is NOT yet installed.
    *   **Recommendation:** You MUST install `axios` as a dependency (`npm install axios`) before implementing the API client. The project already has React Query installed, so you can proceed with creating query hooks immediately after installing Axios.

### Implementation Tips & Notes

*   **CRITICAL:** The task description specifies that you need to handle 401 errors by refreshing the token and retrying the original request. This is a critical piece of logic. You SHOULD implement a response interceptor that: (1) detects 401 status codes, (2) calls the `/api/v1/auth/refresh` endpoint with the stored `refreshToken`, (3) updates the auth store with the new tokens via `setAuth()`, (4) retries the original request with the new access token. If the refresh fails, you MUST call `clearAuth()` and potentially redirect to the login page (though redirection may need to be handled at the component level).

*   **CRITICAL:** The OpenAPI spec shows that the `/api/v1/auth/refresh` endpoint expects a `refreshToken` in the request body as `{"refreshToken": "v1.MR5tqKz..."}`, NOT in a cookie (despite the architecture document's mention of "httpOnly secure cookies"). You MUST use the refresh token from the authStore (which is in localStorage) and send it in the request body.

*   **CRITICAL:** React Query version 5 (which is installed in this project) introduced breaking changes from version 4. The query hooks MUST use the new API: `useQuery({ queryKey: [...], queryFn: async () => {...} })` instead of the old `useQuery([...], async () => {...})` syntax. Ensure you use the correct v5 syntax.

*   **Tip:** The Axios request interceptor should check if the user is authenticated before adding the `Authorization` header. This allows unauthenticated requests (e.g., anonymous room creation) to proceed without a token. You can check `useAuthStore.getState().isAuthenticated` or simply check if `accessToken` exists.

*   **WARNING:** Be very careful with the token refresh retry logic to avoid infinite loops. You MUST track which requests are refresh attempts and NOT retry those if they fail with a 401. Otherwise, a failed refresh will trigger another refresh, which will fail and trigger another refresh, ad infinitum. A common pattern is to use a flag like `_retry: true` on the Axios request config to mark retried requests, or use a separate Axios instance for the refresh call that doesn't use the interceptor.

*   **Tip:** For React Query cache invalidation, you SHOULD use the `useMutation` hook from React Query for write operations (POST, PUT, DELETE). In the mutation's `onSuccess` callback, you can call `queryClient.invalidateQueries({ queryKey: ['rooms'] })` to trigger a refetch of the rooms list after creating a new room. Make sure to pass the `queryClient` instance to your hooks (via `useQueryClient()` from `@tanstack/react-query`).

*   **Note:** The project uses TypeScript with strict mode enabled. You MUST ensure all type definitions are correct and all function return types are explicitly declared. The linter is configured to report unused disable directives, so avoid adding `// @ts-ignore` comments unless absolutely necessary.

*   **Tip:** The API base URL should be configurable. You SHOULD read it from an environment variable (e.g., `import.meta.env.VITE_API_BASE_URL`) with a fallback to `/api/v1` for development. This allows the frontend to point to different backend instances in staging vs. production. Note that Vite uses `import.meta.env` (NOT `process.env`) for environment variables.

*   **Tip:** For error handling, you should create a standardized error handler that can parse the backend's error response format (see `ErrorResponse` schema in openapi.yaml). The backend returns errors as `{error: string, message: string, timestamp: string}`. Your error handler should extract the `message` field for display to users.

*   **Tip:** When implementing the response interceptor for token refresh, you'll need to queue pending requests while the refresh is in progress. Otherwise, if multiple API calls fail with 401 simultaneously (because the token expired), they will all trigger separate refresh attempts. Use a promise that resolves when the refresh completes, and have all pending requests wait for that promise.

*   **Note:** The `useRooms()` hook should fetch the list of rooms for the current user. According to the OpenAPI spec, this is done via `GET /api/v1/users/{userId}/rooms`. You'll need to get the `userId` from the auth store's `user` object. Make sure to handle the case where the user is not authenticated (return an empty result or skip the query).

*   **Tip:** React Query provides built-in support for loading and error states. Your hooks should return these states directly from the `useQuery` result: `{ data, isLoading, error, isError }`. Components can then destructure these values to show loading spinners or error messages.

*   **Security Note:** Store tokens in `localStorage` as specified in the architecture design. While this has XSS vulnerability implications, it matches the project's requirements. The refresh token rotation (single-use refresh tokens) provides some mitigation against token theft.

### Project Structure Observations

*   The frontend uses path aliases configured with `@/` prefix
*   Services go in `frontend/src/services/`
*   Stores go in `frontend/src/stores/`
*   Hooks go in `frontend/src/hooks/`
*   Types go in `frontend/src/types/`
*   The project already has TypeScript configured with strict mode enabled
*   Vite is configured to proxy `/api` requests to the backend at `http://localhost:8080`

### Example Code Patterns

**Axios Instance Configuration:**
```typescript
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});
```

**Request Interceptor Pattern:**
```typescript
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

**React Query v5 Hook Pattern:**
```typescript
export function useUser(userId: string) {
  return useQuery({
    queryKey: ['user', userId],
    queryFn: async () => {
      const response = await apiClient.get<UserDTO>(`/users/${userId}`);
      return response.data;
    },
    enabled: !!userId, // Only run query if userId is provided
  });
}
```

**Mutation with Cache Invalidation:**
```typescript
export function useCreateRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (roomData: CreateRoomRequest) => {
      const response = await apiClient.post<RoomDTO>('/rooms', roomData);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rooms'] });
    },
  });
}
```
