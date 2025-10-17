# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Implement JAX-RS REST controllers for room CRUD operations following OpenAPI specification from I2.T1. Create `RoomController` with endpoints: `POST /api/v1/rooms` (create room), `GET /api/v1/rooms/{roomId}` (get room), `PUT /api/v1/rooms/{roomId}/config` (update config), `DELETE /api/v1/rooms/{roomId}` (delete), `GET /api/v1/users/{userId}/rooms` (list user's rooms). Inject `RoomService`, convert entities to DTOs, handle exceptions (404 for room not found, 400 for validation errors). Add `@RolesAllowed` annotations for authorization (room owner can delete, authenticated users can create). Return reactive `Uni<>` types for non-blocking I/O.

**Deliverables:**
- RoomController with 5 endpoint methods matching OpenAPI spec
- DTO classes for requests and responses
- MapStruct mapper for entity ↔ DTO conversion
- Exception handlers for 404, 400 errors
- Authorization annotations (`@RolesAllowed("USER")`)
- Reactive return types (Uni<Response>)

**Acceptance Criteria:**
- Endpoints accessible via `curl` or Postman against running Quarkus dev server
- POST creates room, returns 201 Created with RoomDTO body
- GET retrieves room by ID, returns 200 OK or 404 Not Found
- PUT updates config, returns 200 OK with updated RoomDTO
- DELETE soft deletes room, returns 204 No Content
- GET user's rooms returns paginated list (if many rooms)
- DTOs match OpenAPI schema definitions exactly
- Authorization prevents unauthorized users from deleting other users' rooms

---

## Issues Detected

*   **Missing Implementation:** NO CODE WAS GENERATED. The git status shows only context file changes, but none of the required target files exist:
    - `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java` - MISSING
    - `backend/src/main/java/com/scrumpoker/api/rest/dto/RoomDTO.java` - MISSING
    - `backend/src/main/java/com/scrumpoker/api/rest/dto/CreateRoomRequest.java` - MISSING
    - `backend/src/main/java/com/scrumpoker/api/rest/dto/UpdateRoomConfigRequest.java` - MISSING
    - `backend/src/main/java/com/scrumpoker/api/rest/mapper/RoomMapper.java` - MISSING

---

## Best Approach to Fix

You MUST implement the complete REST API layer for room management. Follow the detailed implementation guide below.

### 1. Create DTO Package Structure

Create the package `backend/src/main/java/com/scrumpoker/api/rest/dto/` and implement the following DTOs exactly matching the OpenAPI schema:

#### RoomDTO.java
```java
package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class RoomDTO {
    public String roomId;
    public UUID ownerId;
    public UUID organizationId;
    public String title;
    public String privacyMode; // PUBLIC, INVITE_ONLY, ORG_RESTRICTED
    public RoomConfigDTO config;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public LocalDateTime lastActiveAt;

    public List<RoomParticipantDTO> participants;
}
```

#### RoomConfigDTO.java
```java
package com.scrumpoker.api.rest.dto;

import java.util.List;

public class RoomConfigDTO {
    public String deckType; // fibonacci, tshirt, powers_of_2, custom
    public List<String> customDeck;
    public Boolean timerEnabled;
    public Integer timerDurationSeconds;
    public String revealBehavior; // manual, automatic, timer
    public Boolean allowObservers;
    public Boolean allowAnonymousVoters;
}
```

#### CreateRoomRequest.java
```java
package com.scrumpoker.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateRoomRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    public String title;

    public String privacyMode; // Optional, defaults to PUBLIC
    public RoomConfigDTO config; // Optional
}
```

#### UpdateRoomConfigRequest.java
```java
package com.scrumpoker.api.rest.dto;

import jakarta.validation.constraints.Size;

public class UpdateRoomConfigRequest {
    @Size(max = 255, message = "Title must not exceed 255 characters")
    public String title;

    public String privacyMode;
    public RoomConfigDTO config;
}
```

#### RoomListResponse.java
```java
package com.scrumpoker.api.rest.dto;

import java.util.List;

public class RoomListResponse {
    public List<RoomDTO> rooms;
    public Integer page;
    public Integer size;
    public Long totalElements;
    public Integer totalPages;
}
```

#### RoomParticipantDTO.java
```java
package com.scrumpoker.api.rest.dto;

import java.util.UUID;

public class RoomParticipantDTO {
    public UUID userId;
    public String displayName;
    public String avatarUrl;
    public String role; // HOST, VOTER, OBSERVER
}
```

#### ErrorResponse.java
```java
package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.Map;

public class ErrorResponse {
    public String error;
    public String message;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public LocalDateTime timestamp;

    public Map<String, Object> details;

    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
```

### 2. Create MapStruct Mapper

Create `backend/src/main/java/com/scrumpoker/api/rest/mapper/RoomMapper.java`:

```java
package com.scrumpoker.api.rest.mapper;

import com.scrumpoker.api.rest.dto.RoomDTO;
import com.scrumpoker.api.rest.dto.RoomConfigDTO;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.RoomConfig;
import com.scrumpoker.domain.room.PrivacyMode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "cdi")
public interface RoomMapper {

    @Mapping(target = "ownerId", source = "owner.userId")
    @Mapping(target = "organizationId", source = "organization.orgId")
    @Mapping(target = "privacyMode", source = "privacyMode", qualifiedByName = "privacyModeToString")
    @Mapping(target = "config", source = "config", qualifiedByName = "configToDTO")
    @Mapping(target = "participants", ignore = true) // Will be populated separately
    RoomDTO toDTO(Room room);

    @Named("privacyModeToString")
    default String privacyModeToString(PrivacyMode privacyMode) {
        return privacyMode != null ? privacyMode.name() : null;
    }

    @Named("configToDTO")
    default RoomConfigDTO configToDTO(String configJson) {
        // RoomConfig is stored as JSONB string, needs deserialization
        if (configJson == null) return null;
        try {
            return io.quarkus.runtime.util.StringUtil.isNullOrEmpty(configJson)
                ? null
                : new com.fasterxml.jackson.databind.ObjectMapper().readValue(configJson, RoomConfigDTO.class);
        } catch (Exception e) {
            return null;
        }
    }

    RoomConfigDTO toConfigDTO(RoomConfig config);
    RoomConfig toConfig(RoomConfigDTO dto);
}
```

**IMPORTANT:** You MUST add the MapStruct dependency to `backend/pom.xml` if not already present:

```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
    <scope>provided</scope>
</dependency>
```

### 3. Create Exception Mapper

Create `backend/src/main/java/com/scrumpoker/api/rest/exception/RoomNotFoundExceptionMapper.java`:

```java
package com.scrumpoker.api.rest.exception;

import com.scrumpoker.api.rest.dto.ErrorResponse;
import com.scrumpoker.domain.room.RoomNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RoomNotFoundExceptionMapper implements ExceptionMapper<RoomNotFoundException> {

    @Override
    public Response toResponse(RoomNotFoundException exception) {
        ErrorResponse error = new ErrorResponse("NOT_FOUND", exception.getMessage());
        return Response.status(Response.Status.NOT_FOUND)
            .entity(error)
            .build();
    }
}
```

### 4. Create RoomController

Create `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`:

```java
package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.*;
import com.scrumpoker.api.rest.mapper.RoomMapper;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.RoomService;
import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.user.User;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/v1/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomController {

    @Inject
    RoomService roomService;

    @Inject
    RoomMapper roomMapper;

    /**
     * POST /api/v1/rooms - Create new room
     * Security: Allows both authenticated and anonymous access
     * Returns: 201 Created with RoomDTO
     */
    @POST
    public Uni<Response> createRoom(@Valid CreateRoomRequest request) {
        // For now, authentication is not implemented (Iteration 3)
        // Pass null as owner for anonymous rooms
        User owner = null; // TODO: Get from SecurityContext when auth is implemented

        PrivacyMode privacyMode = request.privacyMode != null
            ? PrivacyMode.valueOf(request.privacyMode)
            : PrivacyMode.PUBLIC;

        // Convert RoomConfigDTO to RoomConfig
        var config = request.config != null
            ? roomMapper.toConfig(request.config)
            : null;

        return roomService.createRoom(request.title, privacyMode, owner, config)
            .onItem().transform(room -> {
                RoomDTO dto = roomMapper.toDTO(room);
                return Response.status(Response.Status.CREATED)
                    .entity(dto)
                    .build();
            })
            .onFailure().recoverWithItem(ex -> {
                ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", ex.getMessage());
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(error)
                    .build();
            });
    }

    /**
     * GET /api/v1/rooms/{roomId} - Get room by ID
     * Security: Allows both authenticated and anonymous for public rooms
     * Returns: 200 OK with RoomDTO, or 404 Not Found
     */
    @GET
    @Path("/{roomId}")
    public Uni<Response> getRoom(@PathParam("roomId") String roomId) {
        return roomService.findById(roomId)
            .onItem().transform(room -> {
                RoomDTO dto = roomMapper.toDTO(room);
                return Response.ok(dto).build();
            });
        // RoomNotFoundException is handled by RoomNotFoundExceptionMapper
    }

    /**
     * PUT /api/v1/rooms/{roomId}/config - Update room configuration
     * Security: Requires BearerAuth (authenticated users only)
     * Returns: 200 OK with updated RoomDTO
     */
    @PUT
    @Path("/{roomId}/config")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
    public Uni<Response> updateRoomConfig(
            @PathParam("roomId") String roomId,
            @Valid UpdateRoomConfigRequest request) {

        // TODO: Verify user is the room host when auth is implemented

        Uni<Room> updateUni = Uni.createFrom().nullItem();

        // Update title if provided
        if (request.title != null) {
            updateUni = roomService.updateRoomTitle(roomId, request.title);
        }

        // Update config if provided
        if (request.config != null) {
            var config = roomMapper.toConfig(request.config);
            updateUni = updateUni.flatMap(r -> roomService.updateRoomConfig(roomId, config));
        }

        // If neither title nor config provided, just fetch the room
        if (request.title == null && request.config == null) {
            updateUni = roomService.findById(roomId);
        }

        return updateUni
            .onItem().transform(room -> {
                RoomDTO dto = roomMapper.toDTO(room);
                return Response.ok(dto).build();
            });
    }

    /**
     * DELETE /api/v1/rooms/{roomId} - Soft delete room
     * Security: Requires BearerAuth
     * Returns: 204 No Content
     */
    @DELETE
    @Path("/{roomId}")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
    public Uni<Response> deleteRoom(@PathParam("roomId") String roomId) {
        // TODO: Verify user is the room owner when auth is implemented

        return roomService.deleteRoom(roomId)
            .onItem().transform(room ->
                Response.noContent().build()
            );
    }

    /**
     * GET /api/v1/users/{userId}/rooms - List user's owned rooms
     * Security: Requires BearerAuth. Users can only access their own rooms
     * Returns: 200 OK with RoomListResponse (paginated)
     */
    @GET
    @Path("/users/{userId}/rooms")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
    public Uni<Response> getUserRooms(
            @PathParam("userId") UUID userId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {

        // TODO: Verify user can only access their own rooms when auth is implemented

        // Validate page size
        if (size > 100) {
            ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", "Page size cannot exceed 100");
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity(error).build());
        }

        return roomService.findByOwnerId(userId)
            .collect().asList()
            .onItem().transform(rooms -> {
                // Implement pagination manually
                int totalElements = rooms.size();
                int totalPages = (int) Math.ceil((double) totalElements / size);
                int start = page * size;
                int end = Math.min(start + size, totalElements);

                List<RoomDTO> paginatedRooms = rooms.stream()
                    .skip(start)
                    .limit(size)
                    .map(roomMapper::toDTO)
                    .collect(Collectors.toList());

                RoomListResponse response = new RoomListResponse();
                response.rooms = paginatedRooms;
                response.page = page;
                response.size = size;
                response.totalElements = (long) totalElements;
                response.totalPages = totalPages;

                return Response.ok(response).build();
            });
    }
}
```

### 5. Critical Implementation Notes

*   **Reactive Patterns:** ALL endpoint methods MUST return `Uni<Response>` for non-blocking I/O. Use `.onItem().transform()` for success cases and `.onFailure().recoverWithItem()` for error handling.

*   **Exception Handling:** The `RoomNotFoundException` is automatically mapped to 404 by the `RoomNotFoundExceptionMapper`. You DO NOT need to catch it in controller methods.

*   **Validation:** Use `@Valid` annotation on request bodies to trigger Bean Validation. Quarkus automatically returns 400 Bad Request for validation errors.

*   **Authorization:** `@RolesAllowed("USER")` annotations are present but won't be enforced until authentication is implemented in Iteration 3. This is intentional and prepares the code for future security integration.

*   **Owner Extraction:** Currently, `owner` is set to `null` for all room creation requests since authentication is not implemented. In Iteration 3, you will extract the authenticated user from the security context.

*   **Config Serialization:** The `RoomService` handles JSONB serialization internally. The mapper converts between `RoomConfig` POJO and `RoomConfigDTO`.

*   **Pagination:** The pagination logic in `getUserRooms()` is implemented manually since `RoomService.findByOwnerId()` returns a `Multi<Room>` stream. This is converted to a list and then paginated.

*   **Path Annotations:** The class-level `@Path("/api/v1/rooms")` combined with method-level `@Path("...")` creates the full endpoint paths matching the OpenAPI specification.

*   **MediaType:** `@Produces(MediaType.APPLICATION_JSON)` and `@Consumes(MediaType.APPLICATION_JSON)` ensure all endpoints accept and return JSON.

### 6. Dependency Updates

Ensure the following dependencies are in `backend/pom.xml`:

```xml
<!-- JAX-RS Reactive -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
</dependency>

<!-- Bean Validation -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-validator</artifactId>
</dependency>

<!-- MapStruct -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
    <scope>provided</scope>
</dependency>
```

### 7. Testing Instructions

After implementing, verify with:

```bash
# Start Quarkus dev server
cd backend
mvn quarkus:dev

# Test CREATE room
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"title": "Sprint Planning"}'

# Test GET room (use roomId from CREATE response)
curl http://localhost:8080/api/v1/rooms/{roomId}

# Test UPDATE config
curl -X PUT http://localhost:8080/api/v1/rooms/{roomId}/config \
  -H "Content-Type: application/json" \
  -d '{"title": "Updated Title", "config": {"deckType": "fibonacci"}}'

# Test DELETE room
curl -X DELETE http://localhost:8080/api/v1/rooms/{roomId}

# Test GET user rooms
curl http://localhost:8080/api/v1/users/{userId}/rooms?page=0&size=20
```

All endpoints should return proper JSON responses with correct status codes as specified in the OpenAPI specification.
