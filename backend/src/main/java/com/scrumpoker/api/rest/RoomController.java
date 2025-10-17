package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.*;
import com.scrumpoker.api.rest.mapper.RoomMapper;
import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.RoomConfig;
import com.scrumpoker.domain.room.RoomService;
import com.scrumpoker.domain.user.User;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
@Path("/api/v1/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Rooms", description = "Room management endpoints")
public class RoomController {

    @Inject
    RoomService roomService;

    @Inject
    RoomMapper roomMapper;

    /**
     * POST /api/v1/rooms - Create new room
     * Security: Allows both authenticated and anonymous access (authentication in Iteration 3)
     * Returns: 201 Created with RoomDTO
     */
    @POST
    @Operation(summary = "Create a new room", description = "Creates a new estimation room with the specified configuration")
    @APIResponse(responseCode = "201", description = "Room created successfully",
        content = @Content(schema = @Schema(implementation = RoomDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> createRoom(@Valid CreateRoomRequest request) {
        // For now, authentication is not implemented (Iteration 3)
        // Pass null as owner for anonymous rooms
        User owner = null; // TODO: Get from SecurityContext when auth is implemented

        PrivacyMode privacyMode = request.privacyMode != null
            ? PrivacyMode.valueOf(request.privacyMode.toUpperCase())
            : PrivacyMode.PUBLIC;

        // Convert RoomConfigDTO to RoomConfig
        RoomConfig config = request.config != null
            ? roomMapper.toConfig(request.config)
            : null;

        return roomService.createRoom(request.title, privacyMode, owner, config)
            .onItem().transform(room -> {
                RoomDTO dto = roomMapper.toDTO(room);
                return Response.status(Response.Status.CREATED)
                    .entity(dto)
                    .build();
            });
        // IllegalArgumentException is handled by IllegalArgumentExceptionMapper
    }

    /**
     * GET /api/v1/rooms/{roomId} - Get room by ID
     * Security: Allows both authenticated and anonymous for public rooms
     * Returns: 200 OK with RoomDTO, or 404 Not Found
     */
    @GET
    @Path("/{roomId}")
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
     * Security: Requires authentication (will be enforced in Iteration 3)
     * Returns: 200 OK with updated RoomDTO
     */
    @PUT
    @Path("/{roomId}/config")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
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

        // TODO: Verify user is the room host when auth is implemented

        // Start with the current room state
        Uni<Room> updateChain = roomService.findById(roomId);

        // Update title if provided
        if (request.title != null && !request.title.isEmpty()) {
            updateChain = updateChain.flatMap(room ->
                roomService.updateRoomTitle(roomId, request.title)
            );
        }

        // Update config if provided
        if (request.config != null) {
            RoomConfig config = roomMapper.toConfig(request.config);
            updateChain = updateChain.flatMap(room ->
                roomService.updateRoomConfig(roomId, config)
            );
        }

        return updateChain
            .onItem().transform(room -> {
                RoomDTO dto = roomMapper.toDTO(room);
                return Response.ok(dto).build();
            });
    }

    /**
     * DELETE /api/v1/rooms/{roomId} - Soft delete room
     * Security: Requires authentication (will be enforced in Iteration 3)
     * Returns: 204 No Content
     */
    @DELETE
    @Path("/{roomId}")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
    @Operation(summary = "Delete room", description = "Soft deletes a room (sets deleted_at timestamp)")
    @APIResponse(responseCode = "204", description = "Room deleted successfully")
    @APIResponse(responseCode = "404", description = "Room not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> deleteRoom(
            @Parameter(description = "Room ID (6-character nanoid)", required = true)
            @PathParam("roomId") String roomId) {

        // TODO: Verify user is the room owner when auth is implemented

        return roomService.deleteRoom(roomId)
            .onItem().transform(room ->
                Response.noContent().build()
            );
    }

    /**
     * GET /api/v1/users/{userId}/rooms - List user's owned rooms
     * Security: Requires authentication (will be enforced in Iteration 3)
     * Users can only access their own rooms
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

        // TODO: Verify user can only access their own rooms when auth is implemented

        // Validate page size
        if (size > 100) {
            ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", "Page size cannot exceed 100");
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity(error).build());
        }

        if (page < 0) {
            ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", "Page number must be >= 0");
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
