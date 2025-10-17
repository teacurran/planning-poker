package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.CreateRoomRequest;
import com.scrumpoker.api.rest.dto.RoomConfigDTO;
import com.scrumpoker.api.rest.dto.UpdateRoomConfigRequest;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.RoomRepository;
import com.scrumpoker.repository.UserRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RoomController REST endpoints.
 * Tests end-to-end functionality: HTTP request → controller → service → repository → database → response.
 * Uses @QuarkusTest with Testcontainers PostgreSQL for full integration testing.
 */
@QuarkusTest
@TestProfile(NoSecurityTestProfile.class)
public class RoomControllerTest {

    @Inject
    RoomRepository roomRepository;

    @Inject
    UserRepository userRepository;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up test data before each test
        asserter.execute(() -> Panache.withTransaction(() -> roomRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));
    }

    // ========================================
    // POST /api/v1/rooms - Create Room Tests
    // ========================================

    @Test
    public void testCreateRoom_ValidInput_Returns201() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.title = "Test Room";
        request.privacyMode = "PUBLIC";

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
            .body("roomId", matchesPattern("^[a-z0-9]{6}$"))
            .body("title", equalTo("Test Room"))
            .body("privacyMode", equalTo("PUBLIC"))
            .body("createdAt", notNullValue())
            .body("lastActiveAt", notNullValue())
            .body("config", notNullValue());
    }

    @Test
    public void testCreateRoom_WithCustomConfig_Returns201AndPersistsConfig() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.title = "Fibonacci Room";
        request.privacyMode = "INVITE_ONLY";

        RoomConfigDTO config = new RoomConfigDTO();
        config.deckType = "fibonacci";
        config.timerEnabled = true;
        config.timerDurationSeconds = 120;
        config.revealBehavior = "timer";
        config.allowObservers = true;
        request.config = config;

        String roomId = given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
            .body("roomId", notNullValue())
            .body("title", equalTo("Fibonacci Room"))
            .body("privacyMode", equalTo("INVITE_ONLY"))
            .body("config.deckType", equalTo("fibonacci"))
            .body("config.timerEnabled", equalTo(true))
            .body("config.timerDurationSeconds", equalTo(120))
            .body("config.revealBehavior", equalTo("timer"))
            .body("config.allowObservers", equalTo(true))
        .extract()
            .path("roomId");

        // Verify config persisted in database (JSONB field)
        given()
        .when()
            .get("/api/v1/rooms/" + roomId)
        .then()
            .statusCode(200)
            .body("config.deckType", equalTo("fibonacci"))
            .body("config.timerEnabled", equalTo(true))
            .body("config.timerDurationSeconds", equalTo(120));
    }

    @Test
    public void testCreateRoom_InvalidPrivacyMode_Returns400() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.title = "Invalid Room";
        request.privacyMode = "INVALID_MODE";

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(400)
            .body("error", notNullValue())
            .body("message", notNullValue())
            .body("timestamp", notNullValue());
    }

    @Test
    public void testCreateRoom_MissingTitle_Returns400() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.privacyMode = "PUBLIC";
        // title is not set, which violates @NotBlank

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(400);
    }

    @Test
    public void testCreateRoom_DefaultsToPublic_WhenPrivacyModeNotSpecified() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.title = "Default Privacy Room";
        // privacyMode not set - should default to PUBLIC

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
            .body("privacyMode", equalTo("PUBLIC"));
    }

    // ========================================
    // GET /api/v1/rooms/{roomId} - Get Room Tests
    // ========================================

    @Test
    public void testGetRoom_ExistingRoom_Returns200() {
        // Create a room first
        CreateRoomRequest request = new CreateRoomRequest();
        request.title = "Existing Room";
        request.privacyMode = "PUBLIC";

        String roomId = given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
        .extract()
            .path("roomId");

        // Retrieve the room
        given()
        .when()
            .get("/api/v1/rooms/" + roomId)
        .then()
            .statusCode(200)
            .body("roomId", equalTo(roomId))
            .body("title", equalTo("Existing Room"))
            .body("privacyMode", equalTo("PUBLIC"))
            .body("createdAt", notNullValue())
            .body("lastActiveAt", notNullValue());
    }

    @Test
    public void testGetRoom_NotFound_Returns404() {
        given()
        .when()
            .get("/api/v1/rooms/notfnd")
        .then()
            .statusCode(404)
            .body("error", notNullValue())
            .body("message", notNullValue())
            .body("timestamp", notNullValue());
    }

    // ========================================
    // PUT /api/v1/rooms/{roomId}/config - Update Config Tests
    // ========================================

    @Test
    public void testUpdateRoomConfig_TitleOnly_Returns200() {
        // Create room
        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.title = "Original Title";
        createRequest.privacyMode = "PUBLIC";

        String roomId = given()
            .contentType(ContentType.JSON)
            .body(createRequest)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
        .extract()
            .path("roomId");

        // Update title
        UpdateRoomConfigRequest updateRequest = new UpdateRoomConfigRequest();
        updateRequest.title = "Updated Title";

        given()
            .contentType(ContentType.JSON)
            .body(updateRequest)
        .when()
            .put("/api/v1/rooms/" + roomId + "/config")
        .then()
            .statusCode(200)
            .body("roomId", equalTo(roomId))
            .body("title", equalTo("Updated Title"))
            .body("privacyMode", equalTo("PUBLIC")); // Should remain unchanged
    }

    @Test
    public void testUpdateRoomConfig_PrivacyModeOnly_Returns200() {
        // Create room
        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.title = "Privacy Test Room";
        createRequest.privacyMode = "PUBLIC";

        String roomId = given()
            .contentType(ContentType.JSON)
            .body(createRequest)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
        .extract()
            .path("roomId");

        // Update privacy mode
        UpdateRoomConfigRequest updateRequest = new UpdateRoomConfigRequest();
        updateRequest.privacyMode = "INVITE_ONLY";

        given()
            .contentType(ContentType.JSON)
            .body(updateRequest)
        .when()
            .put("/api/v1/rooms/" + roomId + "/config")
        .then()
            .statusCode(200)
            .body("roomId", equalTo(roomId))
            .body("title", equalTo("Privacy Test Room")) // Should remain unchanged
            .body("privacyMode", equalTo("INVITE_ONLY"));
    }

    @Test
    public void testUpdateRoomConfig_RoomConfigOnly_Returns200() {
        // Create room
        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.title = "Config Update Room";
        createRequest.privacyMode = "PUBLIC";

        String roomId = given()
            .contentType(ContentType.JSON)
            .body(createRequest)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
        .extract()
            .path("roomId");

        // Update room config
        UpdateRoomConfigRequest updateRequest = new UpdateRoomConfigRequest();
        RoomConfigDTO newConfig = new RoomConfigDTO();
        newConfig.deckType = "tshirt";
        newConfig.timerEnabled = false;
        newConfig.allowObservers = false;
        updateRequest.config = newConfig;

        given()
            .contentType(ContentType.JSON)
            .body(updateRequest)
        .when()
            .put("/api/v1/rooms/" + roomId + "/config")
        .then()
            .statusCode(200)
            .body("roomId", equalTo(roomId))
            .body("config.deckType", equalTo("tshirt"))
            .body("config.timerEnabled", equalTo(false))
            .body("config.allowObservers", equalTo(false));
    }

    @Test
    public void testUpdateRoomConfig_InvalidPrivacyMode_Returns400() {
        // Create room
        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.title = "Invalid Update Room";
        createRequest.privacyMode = "PUBLIC";

        String roomId = given()
            .contentType(ContentType.JSON)
            .body(createRequest)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
        .extract()
            .path("roomId");

        // Try to update with invalid privacy mode
        UpdateRoomConfigRequest updateRequest = new UpdateRoomConfigRequest();
        updateRequest.privacyMode = "INVALID_MODE";

        given()
            .contentType(ContentType.JSON)
            .body(updateRequest)
        .when()
            .put("/api/v1/rooms/" + roomId + "/config")
        .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"))
            .body("message", containsString("Invalid privacy mode"))
            .body("message", containsString("INVALID_MODE"));
    }

    @Test
    public void testUpdateRoomConfig_RoomNotFound_Returns404() {
        UpdateRoomConfigRequest updateRequest = new UpdateRoomConfigRequest();
        updateRequest.title = "Updated";

        given()
            .contentType(ContentType.JSON)
            .body(updateRequest)
        .when()
            .put("/api/v1/rooms/notfnd/config")
        .then()
            .statusCode(404)
            .body("error", notNullValue())
            .body("message", notNullValue());
    }

    // ========================================
    // DELETE /api/v1/rooms/{roomId} - Delete Room Tests
    // ========================================

    @Test
    @RunOnVertxContext
    public void testDeleteRoom_ExistingRoom_Returns204AndSoftDeletes(UniAsserter asserter) {
        // Create room
        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.title = "Room to Delete";
        createRequest.privacyMode = "PUBLIC";

        String roomId = given()
            .contentType(ContentType.JSON)
            .body(createRequest)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
        .extract()
            .path("roomId");

        // Delete room
        given()
        .when()
            .delete("/api/v1/rooms/" + roomId)
        .then()
            .statusCode(204);

        // Verify room still exists in database but has deleted_at set
        asserter.assertThat(() -> Panache.withTransaction(() ->
            roomRepository.findById(roomId)
        ), room -> {
            assertThat(room).isNotNull();
            assertThat(room.deletedAt).isNotNull();
            assertThat(room.roomId).isEqualTo(roomId);
        });

        // Verify GET request returns 404 for soft-deleted room
        given()
        .when()
            .get("/api/v1/rooms/" + roomId)
        .then()
            .statusCode(404);
    }

    @Test
    public void testDeleteRoom_NotFound_Returns404() {
        given()
        .when()
            .delete("/api/v1/rooms/notfnd")
        .then()
            .statusCode(404)
            .body("error", notNullValue())
            .body("message", notNullValue());
    }

    // ========================================
    // GET /api/v1/users/{userId}/rooms - List User Rooms Tests
    // ========================================

    @Test
    @RunOnVertxContext
    public void testGetUserRooms_DefaultPagination_Returns200(UniAsserter asserter) {
        // Create a test user
        User testUser = createTestUser("test@example.com", "google", "google-test");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Create rooms using the service by setting owner after creation
        CreateRoomRequest request1 = new CreateRoomRequest();
        request1.title = "User Room 1";
        String roomId1 = given()
            .contentType(ContentType.JSON)
            .body(request1)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
        .extract()
            .path("roomId");

        CreateRoomRequest request2 = new CreateRoomRequest();
        request2.title = "User Room 2";
        String roomId2 = given()
            .contentType(ContentType.JSON)
            .body(request2)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
        .extract()
            .path("roomId");

        // Update rooms to set owner manually in database
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.findById(roomId1).flatMap(room -> {
                room.owner = testUser;
                return roomRepository.persist(room);
            }).flatMap(r1 ->
                roomRepository.findById(roomId2).flatMap(room -> {
                    room.owner = testUser;
                    return roomRepository.persist(room);
                })
            )
        ));

        // Get user's rooms with default pagination
        asserter.execute(() ->
            given()
            .when()
                .get("/api/v1/users/" + testUser.userId + "/rooms")
            .then()
                .statusCode(200)
                .body("rooms", hasSize(2))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalElements", equalTo(2))
                .body("totalPages", equalTo(1))
                .body("rooms[0].title", notNullValue())
        );
    }

    @Test
    @RunOnVertxContext
    public void testGetUserRooms_CustomPagination_Returns200(UniAsserter asserter) {
        // Create a test user
        User testUser = createTestUser("paginated@example.com", "google", "google-page");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Create 3 rooms and assign to user
        for (int i = 1; i <= 3; i++) {
            CreateRoomRequest request = new CreateRoomRequest();
            request.title = "Paginated Room " + i;
            String roomId = given()
                .contentType(ContentType.JSON)
                .body(request)
            .when()
                .post("/api/v1/rooms")
            .then()
                .statusCode(201)
            .extract()
                .path("roomId");

            // Set owner
            asserter.execute(() -> Panache.withTransaction(() ->
                roomRepository.findById(roomId).flatMap(room -> {
                    room.owner = testUser;
                    return roomRepository.persist(room);
                })
            ));
        }

        // Request page 0 with size 2 (should get 2 rooms)
        asserter.execute(() ->
            given()
                .queryParam("page", 0)
                .queryParam("size", 2)
            .when()
                .get("/api/v1/users/" + testUser.userId + "/rooms")
            .then()
                .statusCode(200)
                .body("rooms", hasSize(2))
                .body("page", equalTo(0))
                .body("size", equalTo(2))
                .body("totalElements", equalTo(3))
                .body("totalPages", equalTo(2))
        );

        // Request page 1 with size 2 (should get 1 room)
        asserter.execute(() ->
            given()
                .queryParam("page", 1)
                .queryParam("size", 2)
            .when()
                .get("/api/v1/users/" + testUser.userId + "/rooms")
            .then()
                .statusCode(200)
                .body("rooms", hasSize(1))
                .body("page", equalTo(1))
                .body("size", equalTo(2))
                .body("totalElements", equalTo(3))
        );
    }

    @Test
    public void testGetUserRooms_ExceedMaxPageSize_Returns400() {
        UUID userId = UUID.randomUUID();

        given()
            .queryParam("page", 0)
            .queryParam("size", 101) // Exceeds max of 100
        .when()
            .get("/api/v1/users/" + userId + "/rooms")
        .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"))
            .body("message", containsString("Page size cannot exceed 100"));
    }

    @Test
    public void testGetUserRooms_NegativePage_Returns400() {
        UUID userId = UUID.randomUUID();

        given()
            .queryParam("page", -1)
            .queryParam("size", 20)
        .when()
            .get("/api/v1/users/" + userId + "/rooms")
        .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"))
            .body("message", containsString("Page number must be >= 0"));
    }

    @Test
    @RunOnVertxContext
    public void testGetUserRooms_EmptyList_Returns200(UniAsserter asserter) {
        // Create a user with no rooms
        User testUser = createTestUser("noroomuser@example.com", "google", "google-norooms");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Get user's rooms - should return empty list
        asserter.execute(() ->
            given()
            .when()
                .get("/api/v1/users/" + testUser.userId + "/rooms")
            .then()
                .statusCode(200)
                .body("rooms", hasSize(0))
                .body("totalElements", equalTo(0))
                .body("totalPages", equalTo(0))
        );
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Helper method to create test users.
     * User ID is auto-generated by Hibernate on persist.
     */
    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }
}
