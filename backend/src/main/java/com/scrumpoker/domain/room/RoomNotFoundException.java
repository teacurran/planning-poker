package com.scrumpoker.domain.room;

/**
 * Exception thrown when a room cannot be found by its ID.
 * Typically used for REST API 404 responses.
 */
public class RoomNotFoundException extends RuntimeException {

    private final String roomId;

    /**
     * Constructs a new RoomNotFoundException with the given room ID.
     *
     * @param roomId The room ID that was not found
     */
    public RoomNotFoundException(String roomId) {
        super("Room not found: " + roomId);
        this.roomId = roomId;
    }

    /**
     * Constructs a new RoomNotFoundException with the given room ID and cause.
     *
     * @param roomId The room ID that was not found
     * @param cause The underlying cause
     */
    public RoomNotFoundException(String roomId, Throwable cause) {
        super("Room not found: " + roomId, cause);
        this.roomId = roomId;
    }

    /**
     * Gets the room ID that was not found.
     *
     * @return The room ID
     */
    public String getRoomId() {
        return roomId;
    }
}
