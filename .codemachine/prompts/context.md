# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T5",
  "iteration_id": "I2",
  "iteration_goal": "Implement foundational domain services (Room Service, basic User Service), define REST API contracts (OpenAPI specification), and establish WebSocket protocol specification to enable frontend integration and parallel feature development.",
  "description": "Implement JAX-RS REST controllers for room CRUD operations following OpenAPI specification from I2.T1. Create `RoomController` with endpoints: `POST /api/v1/rooms` (create room), `GET /api/v1/rooms/{roomId}` (get room), `PUT /api/v1/rooms/{roomId}/config` (update config), `DELETE /api/v1/rooms/{roomId}` (delete), `GET /api/v1/users/{userId}/rooms` (list user's rooms). Inject `RoomService`, convert entities to DTOs, handle exceptions (404 for room not found, 400 for validation errors). Add `@RolesAllowed` annotations for authorization (room owner can delete, authenticated users can create). Return reactive `Uni<>` types for non-blocking I/O.",
  "agent_type_hint": "BackendAgent",
  "inputs": "OpenAPI specification from I2.T1 (endpoint definitions), RoomService from I2.T3, JAX-RS reactive patterns",
  "input_files": [
    "api/openapi.yaml",
    "backend/src/main/java/com/scrumpoker/domain/room/RoomService.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/RoomController.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/RoomDTO.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/CreateRoomRequest.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/UpdateRoomConfigRequest.java",
    "backend/src/main/java/com/scrumpoker/api/rest/mapper/RoomMapper.java"
  ],
  "deliverables": "RoomController with 5 endpoint methods matching OpenAPI spec, DTO classes for requests and responses, MapStruct mapper for entity ↔ DTO conversion, Exception handlers for 404, 400 errors, Authorization annotations (`@RolesAllowed(\"USER\")`), Reactive return types (Uni<Response>)",
  "acceptance_criteria": "Endpoints accessible via `curl` or Postman against running Quarkus dev server, POST creates room, returns 201 Created with RoomDTO body, GET retrieves room by ID, returns 200 OK or 404 Not Found, PUT updates config, returns 200 OK with updated RoomDTO, DELETE soft deletes room, returns 204 No Content, GET user's rooms returns paginated list (if many rooms), DTOs match OpenAPI schema definitions exactly, Authorization prevents unauthorized users from deleting other users' rooms",
  "dependencies": ["I2.T1", "I2.T3"],
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

**Room Management:**
- `POST /api/v1/rooms` - Create new room (authenticated or anonymous)
- `GET /api/v1/rooms/{roomId}` - Get room configuration and current state
- `PUT /api/v1/rooms/{roomId}/config` - Update room settings (host only)
- `DELETE /api/v1/rooms/{roomId}` - Delete room (owner only)
- `GET /api/v1/users/{userId}/rooms` - List user's owned rooms
```

### Context: synchronous-rest-pattern (from 04_Behavior_and_Communication.md)

```markdown
##### Synchronous REST (Request/Response)

**Use Cases:**
- Room creation and configuration updates

**Pattern Characteristics:**
- Client blocks waiting for server response (typically <500ms)
- Transactional consistency guaranteed within single database transaction
- Error responses use standard HTTP status codes (4xx client errors, 5xx server errors)

**Example Endpoints:**
- `POST /api/v1/rooms` - Create new estimation room
- `GET /api/v1/rooms/{roomId}` - Retrieve room configuration
```

### Context: api-style (from 04_Behavior_and_Communication.md)

```markdown
#### API Style

**Primary API Style:** **RESTful JSON API (OpenAPI 3.1 Specification)**

**Rationale:**
- **Simplicity & Familiarity:** REST over HTTPS provides well-understood contract for CRUD operations
- **Tooling Ecosystem:** OpenAPI enables automatic client SDK generation
- **Versioning Strategy:** URL-based versioning (`/api/v1/`)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** **THIS FILE ALREADY EXISTS AND IS FULLY IMPLEMENTED!** All 5 required REST endpoints are present with correct implementations, reactive return types, exception handling, DTOs, pagination, and authorization annotations.
    *   **Critical Discovery:** The RoomController already contains:
        - POST /api/v1/rooms (lines 50-79) - Creates room, returns 201 Created
        - GET /api/v1/rooms/{roomId} (lines 86-103) - Returns 200 OK or delegates to RoomNotFoundExceptionMapper for 404
        - PUT /api/v1/rooms/{roomId}/config (lines 110-150) - Updates config/title conditionally, returns 200 OK
        - DELETE /api/v1/rooms/{roomId} (lines 157-174) - Soft deletes, returns 204 No Content
        - GET /api/v1/users/{userId}/rooms (lines 182-235) - Paginated list with manual skip/limit
    *   **Recommendation:** **DO NOT REIMPLEMENT FROM SCRATCH!** Instead, REVIEW the existing implementation carefully against the OpenAPI spec and acceptance criteria. Your task is to VERIFY correctness and TEST the endpoints, not to create new code.
    *   **Current Status:** Authentication TODOs exist (expected for Iteration 3), `@RolesAllowed` annotations are present but not enforced yet (expected).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** Complete domain service with all CRUD operations implemented using reactive types. Handles nanoid generation, JSONB config serialization, business validation, soft deletes.
    *   **Recommendation:** The existing RoomController already injects and uses this service correctly. Verify the service method calls in the controller match the method signatures here.
    *   **Key Methods (already being used in controller):**
        - `createRoom()` - Line 71 of RoomController
        - `findById()` - Lines 97, 128 of RoomController
        - `updateRoomTitle()` - Line 133 of RoomController
        - `updateRoomConfig()` - Line 141 of RoomController
        - `deleteRoom()` - Line 170 of RoomController
        - `findByOwnerId()` - Line 211 of RoomController

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/mapper/RoomMapper.java`
    *   **Summary:** Complete mapper handling entity ↔ DTO conversions with JSONB deserialization.
    *   **Recommendation:** The RoomController already injects and uses this mapper correctly (line 43). The mapper's `toDTO()` method is called throughout the controller.
    *   **Important:** Mapper sets participants to empty list (line 45) with TODO comment - this is expected until WebSocket implementation in I4.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/RoomDTO.java`
    *   **Summary:** Fully implemented DTO matching OpenAPI schema with all required fields and Jackson annotations.
    *   **Recommendation:** Already used in RoomController response bodies. Verify it matches OpenAPI schema at lines 1339-1392.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/exception/RoomNotFoundExceptionMapper.java`
    *   **Summary:** Exception mapper automatically converting RoomNotFoundException to 404 responses.
    *   **Recommendation:** Already functioning in RoomController - when `findById()` throws RoomNotFoundException, this mapper creates 404 response automatically (no explicit exception handling needed in controller).

*   **File:** `api/openapi.yaml`
    *   **Summary:** Complete OpenAPI 3.1 specification with detailed endpoint definitions, schemas, examples.
    *   **Recommendation:** Use this as the VERIFICATION REFERENCE. Compare existing RoomController implementation against:
        - POST /api/v1/rooms (lines 313-349) - Should return 201 Created ✓
        - GET /api/v1/rooms/{roomId} (lines 351-374) - Should return 200/404 ✓
        - PUT /api/v1/rooms/{roomId}/config (lines 397-430) - Should return 200 ✓
        - DELETE /api/v1/rooms/{roomId} (lines 376-395) - Should return 204 ✓
        - GET /api/v1/users/{userId}/rooms (lines 432-456) - Should return paginated RoomListResponse ✓

### Implementation Tips & Notes

*   **Critical Tip:** This task appears to have been completed already! The RoomController.java file exists at line 237 (complete file) with all required functionality.
*   **Your Actual Task:** VERIFY the existing implementation meets all acceptance criteria by:
    1. Starting Quarkus dev mode: `./mvnw quarkus:dev`
    2. Testing POST /api/v1/rooms with curl
    3. Testing GET /api/v1/rooms/{roomId} with valid and invalid IDs
    4. Testing PUT /api/v1/rooms/{roomId}/config
    5. Testing DELETE /api/v1/rooms/{roomId}
    6. Testing GET /api/v1/users/{userId}/rooms with pagination params
    7. Comparing responses against OpenAPI spec
*   **Note:** Authorization annotations are present but won't be enforced until I3 (expected). The controller has TODO comments for auth checks (lines 60, 125, 168, 198).
*   **Note:** Pagination is implemented manually with stream skip/limit (lines 220-224) which is acceptable for MVP.
*   **Testing Pattern:** Based on UserRepositoryTest.java, you should consider creating REST integration tests using REST Assured if tests don't already exist.

### Critical Observations

1. **TASK ALREADY COMPLETE:** The RoomController file exists with 237 lines of fully implemented code
2. **All 5 endpoints present:** POST, GET, PUT, DELETE, GET list
3. **Reactive types used:** All methods return `Uni<Response>`
4. **Exception handling:** Delegates to exception mappers
5. **DTOs exist:** RoomDTO, CreateRoomRequest, UpdateRoomConfigRequest, RoomListResponse, ErrorResponse
6. **Mapper exists:** RoomMapper with toDTO() and toConfig()
7. **OpenAPI annotations:** @Operation, @APIResponse annotations present
8. **Authorization ready:** @RolesAllowed annotations present for future enforcement

### Verification Checklist

Since the code exists, verify these acceptance criteria:

- [ ] Start Quarkus: `cd backend && ./mvnw quarkus:dev`
- [ ] POST /api/v1/rooms returns 201 Created with RoomDTO
- [ ] POST validates title is required (400 if missing)
- [ ] GET /api/v1/rooms/{validId} returns 200 OK with RoomDTO
- [ ] GET /api/v1/rooms/{invalidId} returns 404 Not Found
- [ ] PUT /api/v1/rooms/{roomId}/config with title updates title (200 OK)
- [ ] PUT /api/v1/rooms/{roomId}/config with config updates config (200 OK)
- [ ] PUT /api/v1/rooms/{roomId}/config with both updates both (200 OK)
- [ ] DELETE /api/v1/rooms/{roomId} returns 204 No Content
- [ ] DELETE sets deleted_at (verify room no longer retrievable via GET)
- [ ] GET /api/v1/users/{userId}/rooms returns RoomListResponse
- [ ] Pagination works (page=0, size=5 returns max 5 rooms)
- [ ] Response JSONs match OpenAPI schemas
- [ ] Error responses have correct ErrorResponse structure

### Example Test Commands

```bash
# Start Quarkus dev mode
cd backend
./mvnw quarkus:dev

# In another terminal:

# Test POST - Create room
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"title": "Sprint 42 Planning", "privacyMode": "PUBLIC"}'

# Expected: 201 Created with roomId in response

# Test GET - Retrieve room (use roomId from above)
curl http://localhost:8080/api/v1/rooms/abc123

# Expected: 200 OK with full RoomDTO

# Test GET - Invalid room
curl http://localhost:8080/api/v1/rooms/999999

# Expected: 404 Not Found with ErrorResponse

# Test PUT - Update config
curl -X PUT http://localhost:8080/api/v1/rooms/abc123/config \
  -H "Content-Type: application/json" \
  -d '{"title": "Updated Title", "config": {"deckType": "FIBONACCI", "timerEnabled": true}}'

# Expected: 200 OK with updated RoomDTO

# Test DELETE - Soft delete
curl -X DELETE http://localhost:8080/api/v1/rooms/abc123

# Expected: 204 No Content

# Verify delete worked
curl http://localhost:8080/api/v1/rooms/abc123

# Expected: 404 Not Found (room soft deleted)

# Test pagination (create several rooms first, then)
curl "http://localhost:8080/api/v1/users/00000000-0000-0000-0000-000000000000/rooms?page=0&size=2"

# Expected: 200 OK with RoomListResponse containing pagination metadata
```

---

## 4. Summary

**KEY FINDING:** The task asks you to "implement" RoomController, but the file ALREADY EXISTS with complete implementation of all 5 required endpoints!

**Your actual task should be:**
1. **VERIFY** the existing implementation is correct
2. **TEST** all endpoints against running Quarkus server
3. **DOCUMENT** test results
4. **FIX** any issues found (if any)
5. **MARK TASK COMPLETE** if all acceptance criteria are met

**Do NOT reimplement from scratch** - that would waste time and potentially introduce bugs into working code.

**Expected Outcome:** After testing, if all acceptance criteria pass, mark task I2.T5 as done=true.
