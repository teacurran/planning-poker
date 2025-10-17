package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.*;
import com.scrumpoker.api.rest.mapper.UserMapper;
import com.scrumpoker.domain.user.UserPreferenceConfig;
import com.scrumpoker.domain.user.UserService;
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

import java.util.UUID;

/**
 * REST controller for user profile and preference management operations.
 * Provides endpoints for retrieving and updating user profiles and preferences.
 * Implements OpenAPI specification from I2.T1.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Users", description = "User profile and preferences management endpoints")
public class UserController {

    @Inject
    UserService userService;

    @Inject
    UserMapper userMapper;

    /**
     * GET /api/v1/users/{userId} - Retrieve user profile
     * Security: Requires authentication (will be enforced in Iteration 3)
     * Returns: 200 OK with UserDTO, or 404 Not Found
     */
    @GET
    @Path("/users/{userId}")
    @Operation(summary = "Retrieve user profile",
        description = "Returns public profile information for a user. Users can view their own full profile or other users' public profiles.")
    @APIResponse(responseCode = "200", description = "User profile retrieved",
        content = @Content(schema = @Schema(implementation = UserDTO.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "User not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> getUserProfile(
            @Parameter(description = "User UUID", required = true)
            @PathParam("userId") UUID userId) {

        // TODO: Add authentication check when JWT is implemented in Iteration 3
        // For now, allow any user to view any profile

        return userService.getUserById(userId)
            .onItem().transform(user -> {
                UserDTO dto = userMapper.toDTO(user);
                return Response.ok(dto).build();
            });
        // UserNotFoundException is handled by UserNotFoundExceptionMapper
    }

    /**
     * PUT /api/v1/users/{userId} - Update user profile
     * Security: Requires authentication (will be enforced in Iteration 3)
     * Users can only update their own profile (403 Forbidden otherwise)
     * Returns: 200 OK with updated UserDTO
     */
    @PUT
    @Path("/users/{userId}")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
    @Operation(summary = "Update user profile",
        description = "Updates display name and avatar URL. Users can only update their own profile.")
    @APIResponse(responseCode = "200", description = "Profile updated successfully",
        content = @Content(schema = @Schema(implementation = UserDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden - user trying to update another user's profile",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "User not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> updateUserProfile(
            @Parameter(description = "User UUID", required = true)
            @PathParam("userId") UUID userId,
            @Valid UpdateProfileRequest request) {

        // TODO: Verify authenticated user can only update their own profile when auth is implemented (Iteration 3)
        // TODO: Return 403 Forbidden if user tries to update another user's profile

        return userService.updateProfile(userId, request.displayName, request.avatarUrl)
            .onItem().transform(user -> {
                UserDTO dto = userMapper.toDTO(user);
                return Response.ok(dto).build();
            });
        // UserNotFoundException is handled by UserNotFoundExceptionMapper
        // IllegalArgumentException is handled by IllegalArgumentExceptionMapper
    }

    /**
     * GET /api/v1/users/{userId}/preferences - Get user preferences
     * Security: Requires authentication (will be enforced in Iteration 3)
     * Users can only access their own preferences (403 Forbidden otherwise)
     * Returns: 200 OK with UserPreferenceDTO
     */
    @GET
    @Path("/users/{userId}/preferences")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
    @Operation(summary = "Get user preferences",
        description = "Returns saved user preferences including default room settings, theme, and notification preferences.")
    @APIResponse(responseCode = "200", description = "Preferences retrieved",
        content = @Content(schema = @Schema(implementation = UserPreferenceDTO.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden - user accessing another user's preferences",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "User or preferences not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> getUserPreferences(
            @Parameter(description = "User UUID", required = true)
            @PathParam("userId") UUID userId) {

        // TODO: Verify authenticated user can only access their own preferences when auth is implemented (Iteration 3)
        // TODO: Return 403 Forbidden if user tries to access another user's preferences

        return userService.getPreferences(userId)
            .onItem().transform(preference -> {
                UserPreferenceDTO dto = userMapper.toPreferenceDTO(preference);
                return Response.ok(dto).build();
            });
        // UserNotFoundException is handled by UserNotFoundExceptionMapper
    }

    /**
     * PUT /api/v1/users/{userId}/preferences - Update user preferences
     * Security: Requires authentication (will be enforced in Iteration 3)
     * Users can only update their own preferences (403 Forbidden otherwise)
     * Returns: 200 OK with updated UserPreferenceDTO
     */
    @PUT
    @Path("/users/{userId}/preferences")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
    @Operation(summary = "Update user preferences",
        description = "Updates user preferences for default room configuration, theme, and notifications.")
    @APIResponse(responseCode = "200", description = "Preferences updated",
        content = @Content(schema = @Schema(implementation = UserPreferenceDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden - user trying to update another user's preferences",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "User or preferences not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> updateUserPreferences(
            @Parameter(description = "User UUID", required = true)
            @PathParam("userId") UUID userId,
            @Valid UpdateUserPreferenceRequest request) {

        // TODO: Verify authenticated user can only update their own preferences when auth is implemented (Iteration 3)
        // TODO: Return 403 Forbidden if user tries to update another user's preferences

        // Convert request DTO to UserPreferenceConfig
        UserPreferenceConfig config = userMapper.toConfig(request);

        return userService.updatePreferences(userId, config)
            .onItem().transform(preference -> {
                UserPreferenceDTO dto = userMapper.toPreferenceDTO(preference);
                return Response.ok(dto).build();
            });
        // UserNotFoundException is handled by UserNotFoundExceptionMapper
        // IllegalArgumentException is handled by IllegalArgumentExceptionMapper
    }
}
