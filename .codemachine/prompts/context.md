# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I7.T5",
  "iteration_id": "I7",
  "iteration_goal": "Implement enterprise-tier features including SSO integration (OIDC/SAML2), organization management, member administration, org-level branding, and audit logging.",
  "description": "Implement REST endpoints for organization management per OpenAPI spec. Endpoints: `POST /api/v1/organizations` (create org, Enterprise tier only), `GET /api/v1/organizations/{orgId}` (get org details), `PUT /api/v1/organizations/{orgId}/sso` (configure SSO settings, admin only), `POST /api/v1/organizations/{orgId}/members` (invite member, admin only), `DELETE /api/v1/organizations/{orgId}/members/{userId}` (remove member, admin only), `GET /api/v1/organizations/{orgId}/audit-logs` (query audit trail, admin only). Use `OrganizationService`, `AuditLogService`. Enforce admin role checks (only org admins can modify org).",
  "agent_type_hint": "BackendAgent",
  "inputs": "OpenAPI spec for organization endpoints from I2.T1, OrganizationService from I7.T2",
  "input_files": [
    "api/openapi.yaml",
    "backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/OrganizationController.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/OrganizationDTO.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/SsoConfigRequest.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/InviteMemberRequest.java"
  ],
  "deliverables": "OrganizationController with 6 endpoints, DTOs for organization and SSO config, Admin role enforcement (only admins can update SSO, manage members), Audit log query endpoint with pagination, Enterprise tier enforcement for org creation",
  "acceptance_criteria": "POST /organizations creates org (Enterprise tier only), PUT /sso updates SSO configuration (admin only, 403 for members), POST /members invites user to org, DELETE /members removes user from org, GET /audit-logs returns paginated audit events, Non-admin requests to admin endpoints return 403",
  "dependencies": [
    "I7.T2",
    "I7.T3"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Authentication and Authorization Mechanisms (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: authentication-and-authorization -->
#### Authentication & Authorization

<!-- anchor: authorization-strategy -->
##### Authorization Strategy

**Role-Based Access Control (RBAC):**
- **Roles:** `ANONYMOUS`, `USER`, `PRO_USER`, `ORG_ADMIN`, `ORG_MEMBER`
- **Implementation:** Quarkus Security annotations (`@RolesAllowed`) on REST endpoints and service methods
- **JWT Claims:** Access token includes `roles` array for authorization decisions
- **Dynamic Role Mapping:** Subscription tier (`FREE`, `PRO`, `PRO_PLUS`, `ENTERPRISE`) mapped to roles during token generation

**Resource-Level Permissions:**
- **Room Access:**
  - `PUBLIC` rooms: Accessible to anyone with room ID
  - `INVITE_ONLY` rooms: Requires room owner to whitelist participant (Pro+ tier)
  - `ORG_RESTRICTED` rooms: Requires organization membership (Enterprise tier)
- **Room Operations:**
  - Host controls (reveal, reset, kick): Room creator or user with `HOST` role in `RoomParticipant`
```

### Context: OpenAPI Organization Endpoints (from openapi.yaml, lines 762-959)

```yaml
/api/v1/organizations:
  post:
    tags:
      - Organizations
    summary: Create organization workspace
    description: |
      Creates a new organization workspace. **Requires Enterprise tier subscription.**
    operationId: createOrganization
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/CreateOrganizationRequest'
    responses:
      '201':
        description: Organization created
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/OrganizationDTO'
      '400':
        $ref: '#/components/responses/BadRequest'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '403':
        description: Requires Enterprise subscription
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ErrorResponse'
      '500':
        $ref: '#/components/responses/InternalServerError'

/api/v1/organizations/{orgId}:
  get:
    tags:
      - Organizations
    summary: Get organization settings
    description: |
      Returns organization configuration, branding, and member count. Requires organization membership.
    operationId: getOrganization
    parameters:
      - $ref: '#/components/parameters/OrgIdPath'
    responses:
      '200':
        description: Organization retrieved
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/OrganizationDTO'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '403':
        $ref: '#/components/responses/Forbidden'
      '404':
        $ref: '#/components/responses/NotFound'
      '500':
        $ref: '#/components/responses/InternalServerError'

/api/v1/organizations/{orgId}/sso:
  put:
    tags:
      - Organizations
    summary: Configure OIDC/SAML2 SSO settings
    description: |
      Updates SSO configuration for organization. **Requires ADMIN role.**
    operationId: updateSsoConfig
    parameters:
      - $ref: '#/components/parameters/OrgIdPath'
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/SsoConfigRequest'
    responses:
      '200':
        description: SSO configuration updated
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/OrganizationDTO'
      '400':
        $ref: '#/components/responses/BadRequest'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '403':
        $ref: '#/components/responses/Forbidden'
      '404':
        $ref: '#/components/responses/NotFound'
      '500':
        $ref: '#/components/responses/InternalServerError'

/api/v1/organizations/{orgId}/members:
  post:
    tags:
      - Organizations
    summary: Invite member to organization
    description: |
      Sends invitation email to join organization. **Requires ADMIN role.**
    operationId: inviteMember
    parameters:
      - $ref: '#/components/parameters/OrgIdPath'
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/InviteMemberRequest'
    responses:
      '201':
        description: Member invited
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/OrgMemberDTO'
      '400':
        $ref: '#/components/responses/BadRequest'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '403':
        $ref: '#/components/responses/Forbidden'
      '404':
        $ref: '#/components/responses/NotFound'
      '500':
        $ref: '#/components/responses/InternalServerError'

/api/v1/organizations/{orgId}/members/{userId}:
  delete:
    tags:
      - Organizations
    summary: Remove member from organization
    description: |
      Removes user from organization. **Requires ADMIN role.** Cannot remove last admin.
    operationId: removeMember
    parameters:
      - $ref: '#/components/parameters/OrgIdPath'
      - $ref: '#/components/parameters/UserIdPath'
    responses:
      '204':
        description: Member removed
      '401':
        $ref: '#/components/responses/Unauthorized'
      '403':
        $ref: '#/components/responses/Forbidden'
      '404':
        $ref: '#/components/responses/NotFound'
      '500':
        $ref: '#/components/responses/InternalServerError'

/api/v1/organizations/{orgId}/audit-logs:
  get:
    tags:
      - Organizations
    summary: Query audit trail
    description: |
      Returns paginated audit log entries for compliance. **Requires ADMIN role.**
    operationId: getAuditLogs
    parameters:
      - $ref: '#/components/parameters/OrgIdPath'
      - name: from
        in: query
        schema:
          type: string
          format: date-time
        description: Start timestamp (ISO 8601 format)
        example: "2025-01-01T00:00:00Z"
      - name: to
        in: query
        schema:
          type: string
          format: date-time
        description: End timestamp (ISO 8601 format)
        example: "2025-01-31T23:59:59Z"
      - name: action
        in: query
        schema:
          type: string
        description: Filter by action type (e.g., user.login, room.create)
        example: "user.login"
      - $ref: '#/components/parameters/PageParam'
      - $ref: '#/components/parameters/SizeParam'
    responses:
      '200':
        description: Audit logs retrieved
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AuditLogListResponse'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '403':
        $ref: '#/components/responses/Forbidden'
      '404':
        $ref: '#/components/responses/NotFound'
      '500':
        $ref: '#/components/responses/InternalServerError'
```

### Context: OpenAPI Schema Definitions (from openapi.yaml, lines 1963-2264)

```yaml
OrganizationDTO:
  type: object
  required:
    - orgId
    - name
    - domain
    - createdAt
  properties:
    orgId:
      type: string
      format: uuid
      description: Organization unique identifier
      example: "123e4567-e89b-12d3-a456-426614174000"
    name:
      type: string
      maxLength: 255
      description: Organization display name
      example: "Acme Corporation"
    domain:
      type: string
      maxLength: 255
      description: Organization email domain (unique)
      example: "acme.com"
    ssoConfig:
      $ref: '#/components/schemas/SsoConfigDTO'
    branding:
      $ref: '#/components/schemas/BrandingDTO'
    subscriptionId:
      type: string
      format: uuid
      nullable: true
      description: Associated subscription ID
      example: "550e8400-e29b-41d4-a716-446655440000"
    memberCount:
      type: integer
      description: Total organization members
      example: 42
    createdAt:
      type: string
      format: date-time
      description: Organization creation timestamp
      example: "2025-01-01T00:00:00Z"
    updatedAt:
      type: string
      format: date-time
      description: Last update timestamp
      example: "2025-01-10T15:30:00Z"

SsoConfigDTO:
  type: object
  description: SSO configuration (OIDC or SAML2)
  properties:
    protocol:
      type: string
      enum: [OIDC, SAML2]
    issuer:
      type: string
      format: uri
    clientId:
      type: string
    authorizationEndpoint:
      type: string
      format: uri
    tokenEndpoint:
      type: string
      format: uri
    jwksUri:
      type: string
      format: uri
    samlEntityId:
      type: string
    samlSsoUrl:
      type: string
      format: uri

BrandingDTO:
  type: object
  description: Organization branding customization
  properties:
    logoUrl:
      type: string
      format: uri
      maxLength: 500
    primaryColor:
      type: string
      pattern: '^#[0-9A-Fa-f]{6}$'
    secondaryColor:
      type: string
      pattern: '^#[0-9A-Fa-f]{6}$'

CreateOrganizationRequest:
  type: object
  required:
    - name
    - domain
  properties:
    name:
      type: string
      maxLength: 255
    domain:
      type: string
      maxLength: 255
    branding:
      $ref: '#/components/schemas/BrandingDTO'

SsoConfigRequest:
  type: object
  required:
    - protocol
  properties:
    protocol:
      type: string
      enum: [OIDC, SAML2]
    issuer:
      type: string
      format: uri
    clientId:
      type: string
    clientSecret:
      type: string
      format: password
    authorizationEndpoint:
      type: string
      format: uri
    tokenEndpoint:
      type: string
      format: uri
    jwksUri:
      type: string
      format: uri
    samlEntityId:
      type: string
    samlSsoUrl:
      type: string
      format: uri
    samlCertificate:
      type: string

OrgMemberDTO:
  type: object
  required:
    - userId
    - displayName
    - email
    - role
    - joinedAt
  properties:
    userId:
      type: string
      format: uuid
    displayName:
      type: string
    email:
      type: string
      format: email
    avatarUrl:
      type: string
      format: uri
      nullable: true
    role:
      $ref: '#/components/schemas/OrgRole'
    joinedAt:
      type: string
      format: date-time

InviteMemberRequest:
  type: object
  required:
    - email
    - role
  properties:
    email:
      type: string
      format: email
    role:
      $ref: '#/components/schemas/OrgRole'

AuditLogDTO:
  type: object
  required:
    - logId
    - action
    - resourceType
    - timestamp
  properties:
    logId:
      type: string
      format: uuid
    orgId:
      type: string
      format: uuid
      nullable: true
    userId:
      type: string
      format: uuid
      nullable: true
    action:
      type: string
      maxLength: 100
    resourceType:
      type: string
      maxLength: 50
    resourceId:
      type: string
      maxLength: 255
      nullable: true
    ipAddress:
      type: string
      maxLength: 45
      nullable: true
    userAgent:
      type: string
      maxLength: 500
      nullable: true
    timestamp:
      type: string
      format: date-time

AuditLogListResponse:
  type: object
  required:
    - logs
    - page
    - size
    - totalElements
    - totalPages
  properties:
    logs:
      type: array
      items:
        $ref: '#/components/schemas/AuditLogDTO'
    page:
      type: integer
    size:
      type: integer
    totalElements:
      type: integer
    totalPages:
      type: integer

OrgRole:
  type: string
  enum:
    - ADMIN
    - MEMBER
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
    *   **Summary:** This file contains the complete business logic for organization management with 7 key methods: createOrganization, updateSsoConfig, addMember, removeMember, updateBranding, getOrganization, and getUserOrganizations. All methods return reactive types (Uni/Multi).
    *   **Recommendation:** You MUST import and use this service class. All 6 REST endpoints will delegate to methods in this service. Do NOT duplicate business logic in the controller. This service already handles all validation and transaction management.
    *   **Key Methods to Use:**
        - `createOrganization(name, domain, ownerId)` - Validates Enterprise tier via FeatureGate, enforces domain matching, creates org and adds owner as ADMIN member. Returns `Uni<Organization>`.
        - `updateSsoConfig(orgId, ssoConfig)` - Serializes SsoConfig to JSON and stores in Organization.ssoConfig JSONB field. Returns `Uni<Organization>`.
        - `addMember(orgId, userId, role)` - Validates org and user exist, prevents duplicate membership, creates OrgMember entity. Returns `Uni<OrgMember>`.
        - `removeMember(orgId, userId)` - Prevents removing last admin, deletes member. Returns `Uni<Void>`.
        - `getOrganization(orgId)` - Simple lookup. Returns `Uni<Organization>`.
        - `getUserOrganizations(userId)` - Returns all orgs where user is a member. Returns `Multi<Organization>`.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/AuditLogService.java`
    *   **Summary:** This service provides audit logging with convenience methods for all organization events. It handles IP address extraction, async processing, and JSONB metadata serialization. Critical for Enterprise compliance.
    *   **Recommendation:** You MUST call AuditLogService methods after successful organization operations. The service is fire-and-forget (won't block requests). Use the static `extractIpAddress()` method to get client IP from request headers.
    *   **Key Methods to Use:**
        - `logMemberAdded(orgId, actorUserId, addedUserId, role, ipAddress, userAgent)` - Call after successful member invitation.
        - `logMemberRemoved(orgId, actorUserId, removedUserId, ipAddress, userAgent)` - Call after successful member removal.
        - `logOrgConfigChange(orgId, userId, ipAddress, userAgent, changeDetails)` - Call after SSO config update with before/after values in changeDetails map.
        - `extractIpAddress(xForwardedFor, xRealIp, remoteAddress)` - Static method for IP extraction from headers.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/Organization.java`
    *   **Summary:** JPA entity with UUID primary key (orgId), name, domain (unique constraint), ssoConfig (JSONB String), branding (JSONB String), subscription relationship, and timestamps.
    *   **Recommendation:** This entity stores SSO config and branding as JSON strings. You'll need to deserialize these when mapping to DTOs. Use Jackson ObjectMapper for JSONB handling.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrgMember.java`
    *   **Summary:** JPA entity with composite key (OrgMemberId: orgId + userId), relationships to Organization and User, role (OrgRole enum: ADMIN/MEMBER), and joinedAt timestamp.
    *   **Recommendation:** When mapping to OrgMemberDTO, extract user fields (displayName, email, avatarUrl) from the User entity relationship (member.user.displayName, etc.).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrgRole.java`
    *   **Summary:** Simple enum with two values: ADMIN, MEMBER. Matches database org_role_enum and OpenAPI spec.
    *   **Recommendation:** Use this enum directly in your code. It's already correctly defined.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** Example REST controller showing the standard pattern. Uses `@Path("/api/v1")`, `@Produces/Consumes(MediaType.APPLICATION_JSON)`, `@Tag`, `@Operation`, `@APIResponse` annotations. Returns `Uni<Response>` for all methods.
    *   **Recommendation:** Follow this exact pattern for OrganizationController. Key observations:
        - Use `@RolesAllowed("USER")` for authenticated endpoints
        - Return `Response.status(201).entity(dto).build()` for POST (created)
        - Return `Response.ok(dto).build()` for GET/PUT (success)
        - Return `Response.noContent().build()` for DELETE (success)
        - Inject services with `@Inject` (no constructor needed)
        - Let exception mappers handle errors (don't wrap in try-catch)

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/SubscriptionController.java`
    *   **Summary:** Another controller example demonstrating pagination (GET /billing/invoices), validation, and parallel Uni queries. Shows proper query param handling with `@DefaultValue`.
    *   **Recommendation:** Use this as a reference for GET /audit-logs pagination. Key patterns:
        - Validate page/size params (page >= 0, size <= MAX_PAGE_SIZE)
        - Use `Uni.combine().all().unis(dataUni, countUni).asTuple()` for parallel queries
        - Calculate totalPages: `(int) Math.ceil((double) totalElements / size)`
        - Return response with page, size, totalElements, totalPages fields

*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtAuthenticationFilter.java` (lines 1-100)
    *   **Summary:** This filter populates SecurityIdentity from JWT tokens. The principal name is the userId (as String), and roles are extracted from JWT claims.
    *   **Recommendation:** Inject `SecurityIdentity` in your controller to get current userId. Example:
        ```java
        @Inject
        SecurityIdentity securityIdentity;

        UUID userId = UUID.fromString(securityIdentity.getPrincipal().getName());
        ```
        **CRITICAL:** JWT roles do NOT include organization-specific roles (ORG_ADMIN). You MUST query OrgMemberRepository to check if user is an admin for the specific organization.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/mapper/UserMapper.java`
    *   **Summary:** Example mapper showing JSONB serialization/deserialization using Jackson ObjectMapper. Demonstrates handling null values and fallback logic.
    *   **Recommendation:** Create an OrganizationMapper following this pattern. You'll need to:
        - Deserialize `Organization.ssoConfig` (String) → `SsoConfigDTO` (object)
        - Deserialize `Organization.branding` (String) → `BrandingDTO` (object)
        - Handle null/empty JSONB strings gracefully (return null DTO)
        - Catch JsonProcessingException and log errors (don't fail the whole mapping)

### Implementation Tips & Notes

*   **Tip:** For admin role checking, you CANNOT rely solely on JWT roles. You MUST query OrgMemberRepository to verify the user has ADMIN role for the specific organization. Recommended pattern:
    ```java
    private Uni<OrgMember> requireOrgAdmin(UUID orgId, UUID userId) {
        return orgMemberRepository.findByOrgIdAndUserId(orgId, userId)
            .onItem().ifNull().failWith(() ->
                new ForbiddenException("Not a member of this organization"))
            .onItem().invoke(member -> {
                if (member.role != OrgRole.ADMIN) {
                    throw new ForbiddenException("Requires ADMIN role");
                }
            });
    }
    ```
    Call this before any admin-only operation, then chain with `.flatMap()` to proceed.

*   **Tip:** For IP address and user agent extraction, inject `ContainerRequestContext` and use AuditLogService helper:
    ```java
    @Context
    ContainerRequestContext requestContext;

    String ipAddress = AuditLogService.extractIpAddress(
        requestContext.getHeaderString("X-Forwarded-For"),
        requestContext.getHeaderString("X-Real-IP"),
        requestContext.getHeaderString("Remote-Address")
    );
    String userAgent = requestContext.getHeaderString("User-Agent");
    ```

*   **Note:** OrganizationService.updateSsoConfig expects an `SsoConfig` domain object (from `com.scrumpoker.integration.sso` package), NOT the SsoConfigRequest DTO. You need to create a mapper method to convert SsoConfigRequest → SsoConfig. The SsoConfig class likely has fields matching the DTO.

*   **Note:** Member count in OrganizationDTO should be calculated dynamically. You'll need to add this method to OrgMemberRepository:
    ```java
    public interface OrgMemberRepository extends PanacheRepositoryBase<OrgMember, OrgMemberId> {
        default Uni<Long> countByOrgId(UUID orgId) {
            return count("id.orgId", orgId);
        }
    }
    ```

*   **Warning:** The audit-logs endpoint queries a time-partitioned table. You MUST add this method to AuditLogRepository with proper filtering:
    ```java
    public Uni<List<AuditLog>> findByOrgIdWithFilters(
        UUID orgId,
        Instant from,
        Instant to,
        String action,
        int page,
        int size
    )
    ```
    Use Panache's `find()` with pagination and ensure the query includes the timestamp range for partition pruning.

*   **Warning:** SSO configuration contains sensitive data (clientSecret, certificates). When returning SsoConfigDTO in OrganizationDTO responses, you MUST exclude the clientSecret field. Set it to null in your mapper. Only include clientSecret when updating (SsoConfigRequest).

*   **Tip:** Error handling follows this pattern in the project:
    - Throw `IllegalArgumentException` for validation errors → auto-mapped to 400
    - Throw custom `NotFoundException` for missing resources → auto-mapped to 404
    - Throw `ForbiddenException` for authorization failures → auto-mapped to 403
    - Let service exceptions bubble up (they're already mapped)

*   **Tip:** DTO naming convention: requests end with "Request", responses end with "Response" or "DTO". For nested objects in responses, use "DTO" suffix. Place all DTOs in `backend/src/main/java/com/scrumpoker/api/rest/dto/`.

*   **Note:** The POST /members endpoint receives an email (not userId) in InviteMemberRequest. You MUST first look up the user by email, then call addMember with the found userId. Add this method to UserRepository if missing:
    ```java
    Uni<User> findByEmail(String email);
    ```

*   **Critical:** OrganizationService.removeMember already prevents removing the last admin (throws IllegalStateException). You don't need to duplicate this check in the controller. Just catch the exception and return appropriate error response.

*   **Tip:** For GET /audit-logs pagination, use the same pattern as SubscriptionController.listInvoices():
    - Query params: `from` (Instant), `to` (Instant), `action` (String), `page` (int, default 0), `size` (int, default 20)
    - Validate: page >= 0, size <= 100
    - Parse timestamps using `Instant.parse(fromString)` (ISO-8601 format)
    - Return AuditLogListResponse with logs array, page metadata

*   **Note:** When creating OrganizationDTO from Organization entity, you need to:
    1. Call `orgMemberRepository.countByOrgId(orgId)` to get memberCount
    2. Deserialize `organization.ssoConfig` (JSONB String) to SsoConfigDTO
    3. Deserialize `organization.branding` (JSONB String) to BrandingDTO
    4. Map subscription ID from `organization.subscription.subscriptionId` (if not null)
    This requires parallel Uni queries. Use `Uni.combine().all().unis()` pattern.

*   **Tip:** The SsoConfigRequest has a `branding` field (BrandingDTO) which should map to Organization.branding JSONB. However, looking at the endpoints, branding is only updated via createOrganization (in request body). The updateSsoConfig endpoint ONLY updates SSO config, not branding. Make sure your implementation doesn't incorrectly merge these.

*   **Critical:** Enterprise tier enforcement for POST /organizations is already implemented in OrganizationService.createOrganization (via FeatureGate injection). You don't need to add it in the controller. If the user lacks Enterprise tier, the service will throw FeatureNotAvailableException, which will be auto-mapped to 403.

