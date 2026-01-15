package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.*;
import com.scrumpoker.api.rest.mapper.RoomMapper;
import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.RoomConfig;
import com.scrumpoker.domain.room.RoomService;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.security.SecurityContextImpl;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for room management operations.
 * Provides endpoints for creating, reading, updating, and deleting rooms.
 * Implements OpenAPI specification from I2.T1.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Rooms", description = "Room management endpoints")
public class RoomController {

    @Inject
    RoomService roomService;

    @Inject
    RoomMapper roomMapper;

    @Inject
    SecurityContextImpl securityContext;

    /**
     * POST /api/v1/rooms - Create new room
     * Security: Allows anonymous creation but attaches owner when authenticated
     * Returns: 201 Created with RoomDTO
     */
    @POST
    @Path("/rooms")
    @jakarta.annotation.security.PermitAll
    @Operation(summary = "Create a new room", description = "Creates a new estimation room with the specified configuration")
    @APIResponse(responseCode = "201", description = "Room created successfully",
        content = @Content(schema = @Schema(implementation = RoomDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> createRoom(@Valid CreateRoomRequest request) {
        return resolveOwner()
            .onItem().transformToUni(ownerId -> {
                PrivacyMode privacyMode = resolvePrivacyMode(request.privacyMode, PrivacyMode.PUBLIC);
                RoomConfig config = roomMapper.toConfig(request.config);

                return roomService.createRoomWithOwnerId(request.title, privacyMode, ownerId, config)
                    .onItem().transform(room -> {
                        RoomDTO dto = roomMapper.toDTO(room);
                        return Response.status(Response.Status.CREATED)
                            .entity(dto)
                            .build();
                    });
            });
    }

    /**
     * GET /api/v1/rooms/{roomId} - Get room by ID
     * Security: Allows both authenticated and anonymous for public rooms
     * Returns: 200 OK with RoomDTO, or 404 Not Found
     */
    @GET
    @Path("/rooms/{roomId}")
    @jakarta.annotation.security.PermitAll
    @Operation(summary = "Get room by ID", description = "Retrieves room details by its unique 6-character identifier")
    @APIResponse(responseCode = "200", description = "Room found",
        content = @Content(schema = @Schema(implementation = RoomDTO.class)))
    @APIResponse(responseCode = "404", description = "Room not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> getRoom(
            @Parameter(description = "Room ID (6-character nanoid)", required = true)
            @PathParam("roomId") String roomId) {

        return roomService.findById(roomId)
            .onItem().transform(room -> {
                RoomDTO dto = roomMapper.toDTO(room);
                return Response.ok(dto).build();
            });
        // RoomNotFoundException is handled by RoomNotFoundExceptionMapper
    }

    /**
     * PUT /api/v1/rooms/{roomId}/config - Update room configuration
     * Security: Requires authenticated access (room owner only)
     * Returns: 200 OK with updated RoomDTO
     */
    @PUT
    @Path("/rooms/{roomId}/config")
    @Operation(summary = "Update room configuration", description = "Updates room title, privacy mode, or configuration settings")
    @APIResponse(responseCode = "200", description = "Room updated successfully",
        content = @Content(schema = @Schema(implementation = RoomDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Room not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> updateRoomConfig(
            @Parameter(description = "Room ID (6-character nanoid)", required = true)
            @PathParam("roomId") String roomId,
            @Valid UpdateRoomConfigRequest request) {

        System.out.println("entered updateRoomConfig start " + roomId);
        UUID currentUserId = requireCurrentUserId();

        return ensureRoomOwner(roomId, currentUserId)
            .onItem().transformToUni(room ->
                applyRoomUpdates(roomId, request, room)
            )
            .onItem().transform(updatedRoom -> {
                RoomDTO dto = roomMapper.toDTO(updatedRoom);
                return Response.ok(dto).build();
            });
    }

    /**
     * DELETE /api/v1/rooms/{roomId} - Soft delete room
     * Security: Requires authenticated access (room owner only)
     * Returns: 204 No Content
     */
    @DELETE
    @Path("/rooms/{roomId}")
    @RolesAllowed("USER")
    @Operation(summary = "Delete room", description = "Soft deletes a room (sets deleted_at timestamp)")
    @APIResponse(responseCode = "204", description = "Room deleted successfully")
    @APIResponse(responseCode = "404", description = "Room not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> deleteRoom(
            @Parameter(description = "Room ID (6-character nanoid)", required = true)
            @PathParam("roomId") String roomId) {

        UUID currentUserId = requireCurrentUserId();

        return ensureRoomOwner(roomId, currentUserId)
            .onItem().transformToUni(room ->
                roomService.deleteRoom(roomId)
            )
            .replaceWith(Response.noContent().build());
    }

    /**
     * GET /api/v1/users/{userId}/rooms - List user's owned rooms
     * Security: Requires authentication (users can only access their own rooms)
     * Returns: 200 OK with RoomListResponse (paginated)
     */
    @GET
    @Path("/users/{userId}/rooms")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
    @Operation(summary = "List user's rooms", description = "Retrieves paginated list of rooms owned by a specific user")
    @APIResponse(responseCode = "200", description = "Rooms retrieved successfully",
        content = @Content(schema = @Schema(implementation = RoomListResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid pagination parameters",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> getUserRooms(
            @Parameter(description = "User ID (UUID)", required = true)
            @PathParam("userId") UUID userId,
            @Parameter(description = "Page number (0-indexed)")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (max 100)")
            @QueryParam("size") @DefaultValue("20") int size) {

        UUID currentUserId = requireCurrentUserId();
        if (!userId.equals(currentUserId)) {
            return Uni.createFrom().item(forbiddenResponse("Users can only access their own rooms"));
        }

        // Validate page size
        if (size < 1) {
            return Uni.createFrom().item(badRequest("Page size must be >= 1"));
        }

        if (size > 100) {
            return Uni.createFrom().item(badRequest("Page size cannot exceed 100"));
        }

        if (page < 0) {
            return Uni.createFrom().item(badRequest("Page number must be >= 0"));
        }

        return roomService.findByOwnerId(userId)
            .collect().asList()
            .onItem().transform(rooms -> {
                // Implement pagination manually
                int totalElements = rooms.size();
                if (totalElements == 0 && page > 0) {
                    throw new WebApplicationException(badRequest("Requested page exceeds available pages"));
                }

                int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
                int start = page * size;
                int end = Math.min(start + size, totalElements);

                if (totalElements > 0 && start >= totalElements) {
                    throw new WebApplicationException(badRequest("Requested page exceeds available pages"));
                }

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

    private Uni<UUID> resolveOwner() {
        UUID userId;
        try {
            userId = securityContext.getCurrentUserId();
        } catch (RuntimeException ex) {
            return Uni.createFrom().item((UUID) null);
        }

        if (userId == null) {
            return Uni.createFrom().item((UUID) null);
        }

        return Uni.createFrom().item(userId);
    }

    private UUID requireCurrentUserId() {
        UUID userId;
        try {
            userId = securityContext.getCurrentUserId();
        } catch (RuntimeException ex) {
            throw unauthorizedException();
        }

        if (userId == null) {
            throw unauthorizedException();
        }
        return userId;
    }

    private Uni<Room> ensureRoomOwner(String roomId, UUID userId) {
        return roomService.findById(roomId)
            .onItem().transform(room -> {
                if (!isRoomOwner(room, userId)) {
                    throw forbiddenException("Only the room owner can perform this action");
                }
                return room;
            });
    }

    private Uni<Room> applyRoomUpdates(String roomId, UpdateRoomConfigRequest request, Room currentRoom) {
        Uni<Room> updateChain = Uni.createFrom().item(currentRoom);

        if (request.title != null && !request.title.isBlank()) {
            updateChain = updateChain.flatMap(room ->
                roomService.updateRoomTitle(roomId, request.title));
        }

        if (request.privacyMode != null && !request.privacyMode.isBlank()) {
            PrivacyMode privacyMode = resolvePrivacyMode(request.privacyMode, null);
            updateChain = updateChain.flatMap(room ->
                roomService.updatePrivacyMode(roomId, privacyMode));
        }

        if (request.config != null) {
            RoomConfig config = roomMapper.toConfig(request.config);
            updateChain = updateChain.flatMap(room ->
                roomService.updateRoomConfig(roomId, config));
        }

        return updateChain;
    }

    private PrivacyMode resolvePrivacyMode(String requestedMode, PrivacyMode defaultMode) {
        if (requestedMode == null || requestedMode.isBlank()) {
            return defaultMode;
        }

        try {
            return PrivacyMode.valueOf(requestedMode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid privacy mode: " + requestedMode
                + ". Valid values: PUBLIC, INVITE_ONLY, ORG_RESTRICTED");
        }
    }

    private boolean isRoomOwner(Room room, UUID userId) {
        return room != null
            && room.owner != null
            && room.owner.userId != null
            && room.owner.userId.equals(userId);
    }

    private Response badRequest(String message) {
        ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", message);
        return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
    }

    private Response forbiddenResponse(String message) {
        ErrorResponse error = new ErrorResponse("FORBIDDEN", message);
        return Response.status(Response.Status.FORBIDDEN).entity(error).build();
    }

    private WebApplicationException unauthorizedException() {
        ErrorResponse error = new ErrorResponse("UNAUTHORIZED", "User authentication required");
        return new WebApplicationException(
            Response.status(Response.Status.UNAUTHORIZED).entity(error).build());
    }

    private WebApplicationException forbiddenException(String message) {
        return new WebApplicationException(forbiddenResponse(message));
    }
}
