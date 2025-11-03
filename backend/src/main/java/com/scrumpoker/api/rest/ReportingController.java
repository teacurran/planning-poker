package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.*;
import com.scrumpoker.domain.reporting.*;
import com.scrumpoker.domain.room.SessionHistory;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for reporting and analytics endpoints.
 * Provides session history, detailed reports, and export functionality.
 * Implements tier-based access control via ReportingService.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Reports", description = "Session reporting and analytics endpoints")
public class ReportingController {

    @Inject
    ReportingService reportingService;

    @Inject
    SessionHistoryService sessionHistoryService;

    /**
     * GET /api/v1/reports/sessions - List user's session history with pagination.
     * <p>
     * Returns paginated list of sessions for the authenticated user.
     * Supports filtering by date range and room ID.
     * Default page size is 20, maximum is 100.
     * </p>
     *
     * @param from Start date filter (ISO 8601 format: YYYY-MM-DD), optional
     * @param to End date filter (ISO 8601 format: YYYY-MM-DD), optional
     * @param roomId Room ID filter (6-character nanoid), optional
     * @param page Page number (0-indexed), default 0
     * @param size Page size (1-100), default 20
     * @return Paginated session list with metadata
     */
    @GET
    @Path("/reports/sessions")
    @RolesAllowed("USER")
    @Operation(summary = "List session history",
            description = "Returns paginated session history with filters. " +
                    "Tier Requirements: Free tier (last 30 days, max 10 results), " +
                    "Pro tier (last 90 days, max 100 results), " +
                    "Pro Plus/Enterprise (unlimited history)")
    @APIResponse(responseCode = "200", description = "Session list retrieved",
            content = @Content(schema = @Schema(implementation = SessionListResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Insufficient subscription tier",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> listSessions(
            @Parameter(description = "Start date (ISO 8601 format)", example = "2025-01-01")
            @QueryParam("from") String from,
            @Parameter(description = "End date (ISO 8601 format)", example = "2025-01-31")
            @QueryParam("to") String to,
            @Parameter(description = "Filter by room ID", example = "abc123")
            @QueryParam("roomId") String roomId,
            @Parameter(description = "Page number (0-indexed)")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (1-100)")
            @QueryParam("size") @DefaultValue("20") int size) {

        // TODO: Get authenticated user from SecurityContext when auth is implemented
        // For now, returning empty list
        User currentUser = null;

        // Validate pagination parameters
        if (page < 0) {
            ErrorResponse error = new ErrorResponse("VALIDATION_ERROR",
                    "Page number must be >= 0");
            return Uni.createFrom().item(
                    Response.status(Response.Status.BAD_REQUEST).entity(error).build());
        }

        if (size < 1 || size > 100) {
            ErrorResponse error = new ErrorResponse("VALIDATION_ERROR",
                    "Page size must be between 1 and 100");
            return Uni.createFrom().item(
                    Response.status(Response.Status.BAD_REQUEST).entity(error).build());
        }

        // Parse date parameters
        Instant fromDate;
        Instant toDate;

        try {
            fromDate = from != null
                    ? LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant()
                    : Instant.now().minus(java.time.Duration.ofDays(30));

            toDate = to != null
                    ? LocalDate.parse(to).atTime(23, 59, 59)
                    .toInstant(ZoneOffset.UTC)
                    : Instant.now();
        } catch (java.time.format.DateTimeParseException e) {
            ErrorResponse error = new ErrorResponse("VALIDATION_ERROR",
                    "Invalid date format. Use ISO 8601 format (YYYY-MM-DD)");
            return Uni.createFrom().item(
                    Response.status(Response.Status.BAD_REQUEST).entity(error).build());
        }

        // TODO: When auth is implemented, get user ID from SecurityContext
        // For now, returning mock empty response
        UUID userId = currentUser != null ? currentUser.userId : UUID.randomUUID();

        // Fetch sessions from service
        Uni<List<SessionHistory>> sessionsUni;
        if (roomId != null && !roomId.isEmpty()) {
            // Filter by room and date range
            sessionsUni = sessionHistoryService.getRoomSessionsByDateRange(
                    roomId, fromDate, toDate);
        } else {
            // Filter by user and date range
            sessionsUni = sessionHistoryService.getUserSessions(
                    userId, fromDate, toDate);
        }

        return sessionsUni
                .onItem().transformToUni(sessions -> {
                    // Apply pagination
                    int totalCount = sessions.size();
                    int startIndex = page * size;
                    int endIndex = Math.min(startIndex + size, totalCount);

                    if (startIndex >= totalCount && totalCount > 0) {
                        ErrorResponse error = new ErrorResponse("VALIDATION_ERROR",
                                "Page number exceeds available pages");
                        return Uni.createFrom().item(
                                Response.status(Response.Status.BAD_REQUEST)
                                        .entity(error).build());
                    }

                    List<SessionHistory> paginatedSessions = sessions.stream()
                            .skip(startIndex)
                            .limit(size)
                            .collect(Collectors.toList());

                    // Convert to SessionSummaryDTO
                    // For each session, we need to call reportingService to get summary
                    return io.smallrye.mutiny.Multi.createFrom()
                            .iterable(paginatedSessions)
                            .onItem().transformToUniAndConcatenate(session ->
                                    reportingService.getBasicSessionSummary(
                                            session.id.sessionId))
                            .collect().asList()
                            .onItem().transform(summaries -> {
                                boolean hasNext = endIndex < totalCount;

                                SessionListResponse response = new SessionListResponse(
                                        summaries,
                                        page,
                                        size,
                                        totalCount,
                                        hasNext
                                );

                                return Response.ok(response).build();
                            });
                })
                .onFailure(IllegalArgumentException.class)
                .recoverWithItem(failure -> {
                    ErrorResponse error = new ErrorResponse("VALIDATION_ERROR",
                            failure.getMessage());
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(error).build();
                });
    }

    /**
     * GET /api/v1/reports/sessions/{sessionId} - Get detailed session report.
     * <p>
     * Returns session report with tier-based detail level:
     * - Free tier: Basic summary (average, median, consensus rate)
     * - Pro tier and above: Full round-by-round detail with individual votes
     * </p>
     *
     * @param sessionId Session UUID
     * @return Session report (detail level depends on user's tier)
     */
    @GET
    @Path("/reports/sessions/{sessionId}")
    @RolesAllowed("USER")
    @Operation(summary = "Get detailed session report",
            description = "Returns detailed session report including all rounds and votes. " +
                    "Tier Requirements: Free tier (summary only), " +
                    "Pro tier and above (full round-by-round detail)")
    @APIResponse(responseCode = "200", description = "Session report retrieved",
            content = @Content(schema = @Schema(implementation = DetailedSessionReportDTO.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Access denied or insufficient tier",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Session not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> getSessionReport(
            @Parameter(description = "Session ID", required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000")
            @PathParam("sessionId") UUID sessionId) {

        // TODO: Get authenticated user from SecurityContext when auth is implemented
        User currentUser = null;

        // TODO: Verify user has access to this session (owns it or participated)
        // For now, allowing access

        // Try to get detailed report first (for Pro tier users)
        // If user is Free tier, FeatureGate will throw exception
        // which will be caught and handled by FeatureNotAvailableExceptionMapper
        if (currentUser != null) {
            return reportingService.getDetailedSessionReport(sessionId, currentUser)
                    .onItem().transform(report ->
                            Response.ok(report).build())
                    .onFailure(IllegalArgumentException.class)
                    .recoverWithItem(failure -> {
                        ErrorResponse error = new ErrorResponse("NOT_FOUND",
                                "Session not found: " + sessionId);
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(error).build();
                    });
        } else {
            // No auth yet - return basic summary for all users
            return reportingService.getBasicSessionSummary(sessionId)
                    .onItem().transform(summary ->
                            Response.ok(summary).build())
                    .onFailure(IllegalArgumentException.class)
                    .recoverWithItem(failure -> {
                        ErrorResponse error = new ErrorResponse("NOT_FOUND",
                                "Session not found: " + sessionId);
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(error).build();
                    });
        }
    }

    /**
     * POST /api/v1/reports/export - Create export job for CSV/PDF generation.
     * <p>
     * Creates an asynchronous export job and returns job ID for polling status.
     * Export feature requires Pro tier or higher.
     * </p>
     *
     * @param request Export request (sessionId, format)
     * @return Export job response with job ID
     */
    @POST
    @Path("/reports/export")
    @RolesAllowed("USER")
    @Operation(summary = "Generate export job (CSV/PDF)",
            description = "Creates an asynchronous export job for session data. " +
                    "Returns job ID for polling status. " +
                    "Tier Requirements: Pro tier or higher")
    @APIResponse(responseCode = "202", description = "Export job created",
            content = @Content(schema = @Schema(implementation = ExportJobResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Export requires Pro tier or higher",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> createExportJob(@Valid ExportRequest request) {

        // TODO: Get authenticated user from SecurityContext when auth is implemented
        User currentUser = null;

        // TODO: Verify user has access to this session
        // For now, allowing access

        if (currentUser == null) {
            // Mock user for testing until auth is implemented
            ErrorResponse error = new ErrorResponse("UNAUTHORIZED",
                    "Authentication required");
            return Uni.createFrom().item(
                    Response.status(Response.Status.UNAUTHORIZED).entity(error).build());
        }

        // Generate export via service (which enqueues to Redis Stream)
        return reportingService.generateExport(
                        request.sessionId,
                        request.format,
                        currentUser)
                .onItem().transformToUni(jobIdString -> {
                    // jobIdString is the UUID string returned from enqueueExportJob
                    UUID jobId = UUID.fromString(jobIdString);

                    // Create ExportJob entity in database
                    ExportJob job = new ExportJob();
                    job.jobId = jobId;
                    job.sessionId = request.sessionId;
                    job.user = currentUser;
                    job.format = request.format;
                    job.status = JobStatus.PENDING;

                    return job.persist()
                            .onItem().transform(persisted -> {
                                ExportJobResponse response =
                                        new ExportJobResponse(jobId);
                                return Response.status(Response.Status.ACCEPTED)
                                        .entity(response)
                                        .build();
                            });
                })
                .onFailure(IllegalArgumentException.class)
                .recoverWithItem(failure -> {
                    ErrorResponse error = new ErrorResponse("VALIDATION_ERROR",
                            failure.getMessage());
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(error).build();
                });
    }

    /**
     * GET /api/v1/jobs/{jobId} - Poll export job status.
     * <p>
     * Returns job status (PENDING, PROCESSING, COMPLETED, FAILED).
     * When COMPLETED, includes download URL (expires in 7 days).
     * </p>
     *
     * @param jobId Job UUID
     * @return Job status with download URL (when complete) or error message (when failed)
     */
    @GET
    @Path("/jobs/{jobId}")
    @RolesAllowed("USER")
    @Operation(summary = "Poll export job status",
            description = "Returns job status (PENDING, PROCESSING, COMPLETED, FAILED). " +
                    "When COMPLETED, includes download URL (expires in 24h)")
    @APIResponse(responseCode = "200", description = "Job status retrieved",
            content = @Content(schema = @Schema(implementation = JobStatusResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Job not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> getJobStatus(
            @Parameter(description = "Job ID", required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000")
            @PathParam("jobId") UUID jobId) {

        // TODO: Get authenticated user from SecurityContext when auth is implemented
        // TODO: Verify user owns this job

        return ExportJob.findByJobId(jobId)
                .onItem().ifNull().failWith(() ->
                        new IllegalArgumentException("Job not found: " + jobId))
                .onItem().transform(job -> {
                    Instant completedAt = null;
                    if (job.status == JobStatus.COMPLETED) {
                        completedAt = job.completedAt;
                    } else if (job.status == JobStatus.FAILED) {
                        completedAt = job.failedAt;
                    }

                    JobStatusResponse response = new JobStatusResponse(
                            job.jobId,
                            job.status.name(),
                            job.downloadUrl,
                            job.errorMessage,
                            job.createdAt,
                            completedAt
                    );

                    return Response.ok(response).build();
                })
                .onFailure(IllegalArgumentException.class)
                .recoverWithItem(failure -> {
                    ErrorResponse error = new ErrorResponse("NOT_FOUND",
                            failure.getMessage());
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity(error).build();
                });
    }
}
