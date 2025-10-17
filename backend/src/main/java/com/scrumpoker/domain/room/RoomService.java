package com.scrumpoker.domain.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.RoomRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain service for room management operations.
 * Implements room CRUD with business validation, JSONB config handling,
 * and reactive return types for non-blocking I/O.
 */
@ApplicationScoped
public class RoomService {

    private static final String NANOID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int NANOID_LENGTH = 6;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    RoomRepository roomRepository;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Creates a new room with the given parameters.
     * Generates a unique 6-character nanoid, validates inputs, and initializes JSONB config.
     *
     * @param title The room title (max 255 characters)
     * @param privacyMode The privacy mode (PUBLIC, INVITE_ONLY, ORG_RESTRICTED)
     * @param owner The room owner (nullable for anonymous rooms)
     * @param config The room configuration settings
     * @return Uni containing the created room
     * @throws IllegalArgumentException if title exceeds max length or privacy mode is null
     */
    @WithTransaction
    public Uni<Room> createRoom(String title, PrivacyMode privacyMode, User owner, RoomConfig config) {
        // Validate inputs
        if (title == null || title.trim().isEmpty()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Room title cannot be null or empty"));
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            return Uni.createFrom().failure(new IllegalArgumentException("Room title cannot exceed " + MAX_TITLE_LENGTH + " characters"));
        }
        if (privacyMode == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Privacy mode cannot be null"));
        }

        // Use default config if not provided
        if (config == null) {
            config = new RoomConfig();
        }

        // Create room entity
        Room room = new Room();
        room.roomId = generateNanoid();
        room.title = title.trim();
        room.privacyMode = privacyMode;
        room.owner = owner;
        room.config = serializeConfig(config);
        room.createdAt = Instant.now();
        room.lastActiveAt = Instant.now();

        // Persist and return
        return roomRepository.persist(room);
    }

    /**
     * Updates the configuration of an existing room.
     * Validates the room exists and is not deleted, then updates JSONB config.
     *
     * @param roomId The room ID
     * @param config The new configuration settings
     * @return Uni containing the updated room
     * @throws RoomNotFoundException if room doesn't exist
     */
    @WithTransaction
    public Uni<Room> updateRoomConfig(String roomId, RoomConfig config) {
        if (config == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Room config cannot be null"));
        }

        return findById(roomId)
            .onItem().transform(room -> {
                room.config = serializeConfig(config);
                room.lastActiveAt = Instant.now();
                return room;
            })
            .flatMap(room -> roomRepository.persist(room));
    }

    /**
     * Updates the title of an existing room.
     *
     * @param roomId The room ID
     * @param title The new room title (max 255 characters)
     * @return Uni containing the updated room
     * @throws IllegalArgumentException if title is invalid
     * @throws RoomNotFoundException if room doesn't exist
     */
    @WithTransaction
    public Uni<Room> updateRoomTitle(String roomId, String title) {
        if (title == null || title.trim().isEmpty()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Room title cannot be null or empty"));
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            return Uni.createFrom().failure(new IllegalArgumentException("Room title cannot exceed " + MAX_TITLE_LENGTH + " characters"));
        }

        return findById(roomId)
            .onItem().transform(room -> {
                room.title = title.trim();
                room.lastActiveAt = Instant.now();
                return room;
            })
            .flatMap(room -> roomRepository.persist(room));
    }

    /**
     * Updates the privacy mode of an existing room.
     *
     * @param roomId The room ID
     * @param privacyMode The new privacy mode
     * @return Uni containing the updated room
     * @throws IllegalArgumentException if privacyMode is null
     * @throws RoomNotFoundException if room doesn't exist
     */
    @WithTransaction
    public Uni<Room> updatePrivacyMode(String roomId, PrivacyMode privacyMode) {
        if (privacyMode == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Privacy mode cannot be null"));
        }

        return findById(roomId)
            .onItem().transform(room -> {
                room.privacyMode = privacyMode;
                room.lastActiveAt = Instant.now();
                return room;
            })
            .flatMap(room -> roomRepository.persist(room));
    }

    /**
     * Soft deletes a room by setting the deleted_at timestamp.
     * Does not physically remove the room from the database for audit trail.
     *
     * @param roomId The room ID to delete
     * @return Uni containing the soft-deleted room
     * @throws RoomNotFoundException if room doesn't exist
     */
    @WithTransaction
    public Uni<Room> deleteRoom(String roomId) {
        return findById(roomId)
            .onItem().transform(room -> {
                room.deletedAt = Instant.now();
                return room;
            })
            .flatMap(room -> roomRepository.persist(room));
    }

    /**
     * Finds a room by its unique ID.
     * Only returns active (non-deleted) rooms.
     *
     * @param roomId The room ID (6-character nanoid)
     * @return Uni containing the room
     * @throws RoomNotFoundException if room doesn't exist or is deleted
     */
    @WithSession
    public Uni<Room> findById(String roomId) {
        return roomRepository.findById(roomId)
            .onItem().ifNull().failWith(() -> new RoomNotFoundException(roomId))
            .onItem().transform(room -> {
                // Check if room is soft-deleted
                if (room.deletedAt != null) {
                    throw new RoomNotFoundException(roomId);
                }
                return room;
            });
    }

    /**
     * Finds all active rooms owned by a specific user.
     * Returns rooms ordered by last activity (most recent first).
     *
     * @param ownerId The owner user ID
     * @return Multi stream of rooms owned by the user
     */
    public Multi<Room> findByOwnerId(UUID ownerId) {
        return roomRepository.findActiveByOwnerId(ownerId)
            .onItem().transformToMulti(list -> Multi.createFrom().iterable(list));
    }

    /**
     * Retrieves the configuration of a room.
     * Deserializes the JSONB config column into a RoomConfig POJO.
     *
     * @param roomId The room ID
     * @return Uni containing the room configuration
     * @throws RoomNotFoundException if room doesn't exist
     */
    @WithSession
    public Uni<RoomConfig> getRoomConfig(String roomId) {
        return findById(roomId)
            .onItem().transform(room -> deserializeConfig(room.config));
    }

    /**
     * Generates a unique 6-character nanoid using lowercase letters and digits.
     * Charset: a-z0-9 (36 characters), giving 36^6 = 2,176,782,336 possible combinations.
     *
     * @return A 6-character alphanumeric room ID
     */
    private String generateNanoid() {
        StringBuilder id = new StringBuilder(NANOID_LENGTH);
        for (int i = 0; i < NANOID_LENGTH; i++) {
            id.append(NANOID_ALPHABET.charAt(RANDOM.nextInt(NANOID_ALPHABET.length())));
        }
        return id.toString();
    }

    /**
     * Serializes a RoomConfig POJO to JSON string for JSONB storage.
     *
     * @param config The configuration to serialize
     * @return JSON string representation
     * @throws RuntimeException if serialization fails
     */
    private String serializeConfig(RoomConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize room configuration", e);
        }
    }

    /**
     * Deserializes a JSON string from JSONB storage to RoomConfig POJO.
     *
     * @param configJson The JSON string to deserialize
     * @return The deserialized configuration object
     * @throws RuntimeException if deserialization fails
     */
    private RoomConfig deserializeConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, RoomConfig.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize room configuration", e);
        }
    }
}
