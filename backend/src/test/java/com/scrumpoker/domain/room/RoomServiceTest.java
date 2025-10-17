package com.scrumpoker.domain.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.RoomRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RoomService.
 * Tests room CRUD operations, nanoid generation, JSONB serialization,
 * soft delete behavior, and business validation.
 */
@QuarkusTest
class RoomServiceTest {

    @Inject
    RoomService roomService;

    @Inject
    RoomRepository roomRepository;

    @Inject
    ObjectMapper objectMapper;

    private User testOwner;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up any existing test data in correct order (rooms before users)
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.deleteAll().replaceWith(Uni.createFrom().voidItem())
        ));

        // Create a test owner
        testOwner = createTestUser("owner@example.com", "google", "google-owner");
    }

    @Test
    @RunOnVertxContext
    void testCreateRoom_GeneratesUniqueNanoid(UniAsserter asserter) {
        // Given: valid room parameters
        String title = "Test Room";
        PrivacyMode privacyMode = PrivacyMode.PUBLIC;
        RoomConfig config = new RoomConfig();

        // When: creating a room
        asserter.assertThat(() -> Panache.withTransaction(() ->
            roomService.createRoom(title, privacyMode, testOwner, config)
        ), room -> {
            // Then: room ID is a 6-character alphanumeric string
            assertThat(room.roomId).isNotNull();
            assertThat(room.roomId).hasSize(6);
            assertThat(room.roomId).matches("[a-z0-9]{6}");
            assertThat(room.title).isEqualTo("Test Room");
            assertThat(room.privacyMode).isEqualTo(PrivacyMode.PUBLIC);
            assertThat(room.config).isNotNull();
            assertThat(room.deletedAt).isNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testCreateRoom_NanoidCollisionResistance(UniAsserter asserter) {
        // Given: we will create 1000 rooms
        Set<String> roomIds = new HashSet<>();

        // When: creating 1000 rooms
        for (int i = 0; i < 1000; i++) {
            final int index = i;
            asserter.execute(() -> Panache.withTransaction(() ->
                roomService.createRoom("Room " + index, PrivacyMode.PUBLIC, null, new RoomConfig())
                    .onItem().transform(room -> {
                        roomIds.add(room.roomId);
                        return room;
                    })
            ));
        }

        // Then: all room IDs should be unique
        asserter.execute(() -> {
            assertThat(roomIds).hasSize(1000);
        });
    }

    @Test
    @RunOnVertxContext
    void testCreateRoom_ValidatesTitle(UniAsserter asserter) {
        // Given: invalid title parameters
        RoomConfig config = new RoomConfig();

        // When/Then: null title throws exception
        asserter.execute(() -> {
            try {
                roomService.createRoom(null, PrivacyMode.PUBLIC, null, config);
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).contains("title cannot be null");
            }
        });

        // When/Then: empty title throws exception
        asserter.execute(() -> {
            try {
                roomService.createRoom("   ", PrivacyMode.PUBLIC, null, config);
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).contains("title cannot be null or empty");
            }
        });

        // When/Then: title exceeding 255 characters throws exception
        String longTitle = "a".repeat(256);
        asserter.execute(() -> {
            try {
                roomService.createRoom(longTitle, PrivacyMode.PUBLIC, null, config);
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).contains("cannot exceed 255 characters");
            }
        });
    }

    @Test
    @RunOnVertxContext
    void testCreateRoom_ValidatesPrivacyMode(UniAsserter asserter) {
        // When/Then: null privacy mode throws exception
        asserter.execute(() -> {
            try {
                roomService.createRoom("Test", null, null, new RoomConfig());
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).contains("Privacy mode cannot be null");
            }
        });
    }

    @Test
    @RunOnVertxContext
    void testCreateRoom_WithDefaultConfig(UniAsserter asserter) {
        // Given: no config provided (null)
        // When: creating a room with null config
        asserter.assertThat(() -> Panache.withTransaction(() ->
            roomService.createRoom("Test Room", PrivacyMode.PUBLIC, null, null)
        ), room -> {
            // Then: default config is applied
            assertThat(room.config).isNotNull();
            assertThat(room.config).contains("FIBONACCI"); // default deck type
        });
    }

    @Test
    @RunOnVertxContext
    void testCreateRoom_SerializesConfigToJSON(UniAsserter asserter) {
        // Given: custom room configuration
        RoomConfig config = new RoomConfig();
        config.setDeckType("T_SHIRT");
        config.setTimerEnabled(true);
        config.setTimerDurationSeconds(120);
        config.setRevealBehavior("AUTOMATIC");
        config.setAllowObservers(false);

        // When: creating a room
        asserter.assertThat(() -> Panache.withTransaction(() ->
            roomService.createRoom("Test Room", PrivacyMode.INVITE_ONLY, testOwner, config)
        ), room -> {
            // Then: config is serialized to JSONB
            assertThat(room.config).isNotNull();
            assertThat(room.config).contains("T_SHIRT");
            assertThat(room.config).contains("\"timer_enabled\":true");
            assertThat(room.config).contains("\"timer_duration_seconds\":120");
            assertThat(room.config).contains("AUTOMATIC");
            assertThat(room.config).contains("\"allow_observers\":false");
        });
    }

    @Test
    @RunOnVertxContext
    void testUpdateRoomConfig_UpdatesConfiguration(UniAsserter asserter) {
        // Given: an existing room
        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.createRoom("Test Room", PrivacyMode.PUBLIC, null, new RoomConfig())
        ));

        // Get the room ID for update
        String[] roomIdHolder = new String[1];
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.findPublicRooms().flatMap(rooms -> {
                roomIdHolder[0] = rooms.get(0).roomId;
                return Uni.createFrom().voidItem();
            })
        ));

        // When: updating the room config
        RoomConfig newConfig = new RoomConfig();
        newConfig.setDeckType("CUSTOM");
        newConfig.setTimerEnabled(true);
        newConfig.setTimerDurationSeconds(90);

        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.updateRoomConfig(roomIdHolder[0], newConfig)
        ));

        // Then: config is updated
        asserter.assertThat(() -> Panache.withTransaction(() ->
            roomService.findById(roomIdHolder[0])
        ), updated -> {
            assertThat(updated.config).contains("CUSTOM");
            assertThat(updated.config).contains("\"timer_enabled\":true");
            assertThat(updated.config).contains("\"timer_duration_seconds\":90");
        });
    }

    @Test
    @RunOnVertxContext
    void testUpdateRoomConfig_ValidatesInput(UniAsserter asserter) {
        // Given: an existing room
        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.createRoom("Test Room", PrivacyMode.PUBLIC, null, new RoomConfig())
        ));

        // Get the room ID
        String[] roomIdHolder = new String[1];
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.findPublicRooms().flatMap(rooms -> {
                roomIdHolder[0] = rooms.get(0).roomId;
                return Uni.createFrom().voidItem();
            })
        ));

        // When/Then: null config throws exception
        asserter.execute(() -> {
            try {
                roomService.updateRoomConfig(roomIdHolder[0], null);
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).contains("config cannot be null");
            }
        });
    }

    @Test
    @RunOnVertxContext
    void testUpdateRoomTitle_UpdatesTitle(UniAsserter asserter) {
        // Given: an existing room
        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.createRoom("Original Title", PrivacyMode.PUBLIC, null, new RoomConfig())
        ));

        // Get the room ID
        String[] roomIdHolder = new String[1];
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.findPublicRooms().flatMap(rooms -> {
                roomIdHolder[0] = rooms.get(0).roomId;
                return Uni.createFrom().voidItem();
            })
        ));

        // When: updating the title
        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.updateRoomTitle(roomIdHolder[0], "Updated Title")
        ));

        // Then: title is updated
        asserter.assertThat(() -> Panache.withTransaction(() ->
            roomService.findById(roomIdHolder[0])
        ), updated -> {
            assertThat(updated.title).isEqualTo("Updated Title");
        });
    }

    @Test
    @RunOnVertxContext
    void testDeleteRoom_SoftDeletesRoom(UniAsserter asserter) {
        // Given: an existing room
        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.createRoom("Test Room", PrivacyMode.PUBLIC, null, new RoomConfig())
        ));

        // Get the room ID
        String[] roomIdHolder = new String[1];
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.findPublicRooms().flatMap(rooms -> {
                roomIdHolder[0] = rooms.get(0).roomId;
                return Uni.createFrom().voidItem();
            })
        ));

        // When: deleting the room
        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.deleteRoom(roomIdHolder[0])
        ));

        // Then: room has deletedAt timestamp set
        asserter.assertThat(() -> Panache.withTransaction(() ->
            roomRepository.findById(roomIdHolder[0])
        ), deleted -> {
            assertThat(deleted).isNotNull(); // Still exists in DB
            assertThat(deleted.deletedAt).isNotNull(); // But has deletedAt set
        });

        // And: room is not returned by findById service method
        asserter.assertFailedWith(() -> Panache.withTransaction(() ->
            roomService.findById(roomIdHolder[0])
        ), thrown -> {
            assertThat(thrown).isInstanceOf(RoomNotFoundException.class);
            assertThat(thrown.getMessage()).contains("Room not found: " + roomIdHolder[0]);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindById_ReturnsRoom(UniAsserter asserter) {
        // Given: an existing room
        String[] roomIdHolder = new String[1];
        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.createRoom("Test Room", PrivacyMode.PUBLIC, null, new RoomConfig())
                .onItem().transform(room -> {
                    roomIdHolder[0] = room.roomId;
                    return room;
                })
        ));

        // When: finding the room by ID
        asserter.assertThat(() -> Panache.withTransaction(() ->
            roomService.findById(roomIdHolder[0])
        ), found -> {
            // Then: room is found
            assertThat(found).isNotNull();
            assertThat(found.roomId).isEqualTo(roomIdHolder[0]);
            assertThat(found.title).isEqualTo("Test Room");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindById_ThrowsExceptionForNonExistentRoom(UniAsserter asserter) {
        // When/Then: finding non-existent room throws exception
        asserter.assertFailedWith(() -> Panache.withTransaction(() ->
            roomService.findById("abc123")
        ), thrown -> {
            assertThat(thrown).isInstanceOf(RoomNotFoundException.class);
            assertThat(thrown.getMessage()).contains("Room not found: abc123");
        });
    }

    @Test
    @RunOnVertxContext
    void testGetRoomConfig_DeserializesJSON(UniAsserter asserter) {
        // Given: a room with custom config
        RoomConfig originalConfig = new RoomConfig();
        originalConfig.setDeckType("CUSTOM");
        originalConfig.setTimerEnabled(true);
        originalConfig.setTimerDurationSeconds(180);
        originalConfig.setRevealBehavior("AUTOMATIC");
        originalConfig.setAllowObservers(false);

        String[] roomIdHolder = new String[1];
        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.createRoom("Test Room", PrivacyMode.PUBLIC, null, originalConfig)
                .onItem().transform(room -> {
                    roomIdHolder[0] = room.roomId;
                    return room;
                })
        ));

        // When: retrieving the room config
        asserter.assertThat(() -> Panache.withTransaction(() ->
            roomService.getRoomConfig(roomIdHolder[0])
        ), config -> {
            // Then: config is deserialized correctly
            assertThat(config).isNotNull();
            assertThat(config.getDeckType()).isEqualTo("CUSTOM");
            assertThat(config.isTimerEnabled()).isTrue();
            assertThat(config.getTimerDurationSeconds()).isEqualTo(180);
            assertThat(config.getRevealBehavior()).isEqualTo("AUTOMATIC");
            assertThat(config.isAllowObservers()).isFalse();
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByOwnerId_ReturnsOwnerRooms(UniAsserter asserter) {
        // Given: rooms with different owners
        User owner1 = createTestUser("owner1@example.com", "google", "google-owner1");
        User owner2 = createTestUser("owner2@example.com", "google", "google-owner2");

        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.createRoom("Room 1", PrivacyMode.PUBLIC, owner1, new RoomConfig())
        ));
        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.createRoom("Room 2", PrivacyMode.PUBLIC, owner1, new RoomConfig())
        ));
        asserter.execute(() -> Panache.withTransaction(() ->
            roomService.createRoom("Room 3", PrivacyMode.PUBLIC, owner2, new RoomConfig())
        ));

        // When: finding rooms by owner1
        asserter.assertThat(() -> Panache.withTransaction(() ->
            roomService.findByOwnerId(owner1.userId).collect().asList()
        ), rooms -> {
            // Then: only owner1's rooms are returned
            assertThat(rooms).hasSize(2);
            assertThat(rooms).allMatch(room -> room.owner.userId.equals(owner1.userId));
        });
    }

    @Test
    @RunOnVertxContext
    void testCreateRoom_HandlesAnonymousOwner(UniAsserter asserter) {
        // Given: null owner (anonymous room)
        // When: creating a room with null owner
        asserter.assertThat(() -> Panache.withTransaction(() ->
            roomService.createRoom("Anonymous Room", PrivacyMode.PUBLIC, null, new RoomConfig())
        ), room -> {
            // Then: room is created successfully
            assertThat(room.roomId).isNotNull();
            assertThat(room.owner).isNull();
            assertThat(room.title).isEqualTo("Anonymous Room");
        });
    }

    /**
     * Helper method to create test users.
     */
    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        user.userId = java.util.UUID.randomUUID(); // Set manually for test
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }
}
