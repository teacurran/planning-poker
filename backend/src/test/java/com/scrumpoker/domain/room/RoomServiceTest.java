package com.scrumpoker.domain.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.RoomRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RoomService using Mockito mocks.
 * Tests business logic in isolation without database dependencies.
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    RoomRepository roomRepository;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    RoomService roomService;

    private RoomConfig testConfig;
    private User testOwner;

    @BeforeEach
    void setUp() {
        testConfig = new RoomConfig();
        testConfig.setDeckType("FIBONACCI");
        testConfig.setTimerEnabled(false);
        testConfig.setAllowObservers(true);

        testOwner = new User();
        testOwner.userId = UUID.randomUUID();
        testOwner.email = "owner@example.com";
        testOwner.displayName = "Test Owner";
    }

    // ===== Create Room Tests =====

    @Test
    void testCreateRoom_ValidInput_ReturnsRoom() throws JsonProcessingException {
        // Given
        String title = "Test Room";
        String configJson = "{\"deckType\":\"FIBONACCI\"}";
        Room expectedRoom = new Room();
        expectedRoom.roomId = "abc123";
        expectedRoom.title = title;

        when(objectMapper.writeValueAsString(any(RoomConfig.class))).thenReturn(configJson);
        when(roomRepository.persist(any(Room.class))).thenReturn(Uni.createFrom().item(expectedRoom));

        // When
        Room result = roomService.createRoom(title, PrivacyMode.PUBLIC, null, testConfig)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.roomId).isEqualTo("abc123");
        assertThat(result.title).isEqualTo(title);
        verify(roomRepository).persist(any(Room.class));
        verify(objectMapper).writeValueAsString(testConfig);
    }

    @Test
    void testCreateRoom_GeneratesNanoidWithCorrectFormat() throws JsonProcessingException {
        // Given
        String configJson = "{}";
        when(objectMapper.writeValueAsString(any())).thenReturn(configJson);
        when(roomRepository.persist(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            return Uni.createFrom().item(room);
        });

        // When
        Room result = roomService.createRoom("Test", PrivacyMode.PUBLIC, null, testConfig)
                .await().indefinitely();

        // Then
        assertThat(result.roomId).isNotNull();
        assertThat(result.roomId).hasSize(6);
        assertThat(result.roomId).matches("[a-z0-9]{6}");
    }

    @Test
    void testCreateRoom_NullTitle_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                roomService.createRoom(null, PrivacyMode.PUBLIC, null, testConfig)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title cannot be null");

        verify(roomRepository, never()).persist(any(Room.class));
    }

    @Test
    void testCreateRoom_EmptyTitle_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                roomService.createRoom("   ", PrivacyMode.PUBLIC, null, testConfig)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title cannot be null or empty");

        verify(roomRepository, never()).persist(any(Room.class));
    }

    @Test
    void testCreateRoom_TitleTooLong_ThrowsException() {
        // Given
        String longTitle = "a".repeat(256);

        // When/Then
        assertThatThrownBy(() ->
                roomService.createRoom(longTitle, PrivacyMode.PUBLIC, null, testConfig)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed 255 characters");

        verify(roomRepository, never()).persist(any(Room.class));
    }

    @Test
    void testCreateRoom_NullPrivacyMode_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                roomService.createRoom("Test", null, null, testConfig)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Privacy mode cannot be null");

        verify(roomRepository, never()).persist(any(Room.class));
    }

    @Test
    void testCreateRoom_NullConfig_UsesDefaultConfig() throws JsonProcessingException {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(roomRepository.persist(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            return Uni.createFrom().item(room);
        });

        // When
        Room result = roomService.createRoom("Test", PrivacyMode.PUBLIC, null, null)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        verify(objectMapper).writeValueAsString(any(RoomConfig.class)); // Default config created
        verify(roomRepository).persist(any(Room.class));
    }

    @Test
    void testCreateRoom_TrimsTitle() throws JsonProcessingException {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(roomRepository.persist(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            return Uni.createFrom().item(room);
        });

        // When
        Room result = roomService.createRoom("  Trimmed Title  ", PrivacyMode.PUBLIC, null, testConfig)
                .await().indefinitely();

        // Then
        assertThat(result.title).isEqualTo("Trimmed Title");
    }

    @Test
    void testCreateRoom_WithOwner_SetsOwner() throws JsonProcessingException {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(roomRepository.persist(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            return Uni.createFrom().item(room);
        });

        // When
        Room result = roomService.createRoom("Test", PrivacyMode.PUBLIC, testOwner, testConfig)
                .await().indefinitely();

        // Then
        assertThat(result.owner).isEqualTo(testOwner);
    }

    @Test
    void testCreateRoom_SerializationFailure_ThrowsRuntimeException() throws JsonProcessingException {
        // Given
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization error") {});

        // When/Then
        assertThatThrownBy(() ->
                roomService.createRoom("Test", PrivacyMode.PUBLIC, null, testConfig)
                        .await().indefinitely()
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to serialize room configuration");

        verify(roomRepository, never()).persist(any(Room.class));
    }

    // ===== Update Room Config Tests =====

    @Test
    void testUpdateRoomConfig_ValidInput_UpdatesConfig() throws JsonProcessingException {
        // Given
        String roomId = "room123";
        Room existingRoom = createTestRoom(roomId, "Test Room");
        String newConfigJson = "{\"deckType\":\"T_SHIRT\"}";

        when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(existingRoom));
        when(objectMapper.writeValueAsString(any())).thenReturn(newConfigJson);
        when(roomRepository.persist(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            return Uni.createFrom().item(room);
        });

        // When
        Room result = roomService.updateRoomConfig(roomId, testConfig)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.config).isEqualTo(newConfigJson);
        verify(roomRepository).findById(roomId);
        verify(objectMapper).writeValueAsString(testConfig);
        verify(roomRepository).persist(any(Room.class));
    }

    @Test
    void testUpdateRoomConfig_NullConfig_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                roomService.updateRoomConfig("room123", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config cannot be null");

        verify(roomRepository, never()).findById(anyString());
    }

    @Test
    void testUpdateRoomConfig_RoomNotFound_ThrowsException() {
        // Given
        when(roomRepository.findById(anyString())).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                roomService.updateRoomConfig("nonexistent", testConfig)
                        .await().indefinitely()
        )
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository).findById("nonexistent");
        verify(roomRepository, never()).persist(any(Room.class));
    }

    @Test
    void testUpdateRoomConfig_DeletedRoom_ThrowsException() {
        // Given
        String roomId = "deleted123";
        Room deletedRoom = createTestRoom(roomId, "Deleted Room");
        deletedRoom.deletedAt = Instant.now();

        when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(deletedRoom));

        // When/Then
        assertThatThrownBy(() ->
                roomService.updateRoomConfig(roomId, testConfig)
                        .await().indefinitely()
        )
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository).findById(roomId);
        verify(roomRepository, never()).persist(any(Room.class));
    }

    // ===== Update Room Title Tests =====

    @Test
    void testUpdateRoomTitle_ValidInput_UpdatesTitle() throws JsonProcessingException {
        // Given
        String roomId = "room123";
        Room existingRoom = createTestRoom(roomId, "Old Title");

        when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(existingRoom));
        when(roomRepository.persist(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            return Uni.createFrom().item(room);
        });

        // When
        Room result = roomService.updateRoomTitle(roomId, "New Title")
                .await().indefinitely();

        // Then
        assertThat(result.title).isEqualTo("New Title");
        verify(roomRepository).findById(roomId);
        verify(roomRepository).persist(any(Room.class));
    }

    @Test
    void testUpdateRoomTitle_TrimsTitle() {
        // Given
        String roomId = "room123";
        Room existingRoom = createTestRoom(roomId, "Old Title");

        when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(existingRoom));
        when(roomRepository.persist(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            return Uni.createFrom().item(room);
        });

        // When
        Room result = roomService.updateRoomTitle(roomId, "  Trimmed  ")
                .await().indefinitely();

        // Then
        assertThat(result.title).isEqualTo("Trimmed");
    }

    @Test
    void testUpdateRoomTitle_NullTitle_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                roomService.updateRoomTitle("room123", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title cannot be null");

        verify(roomRepository, never()).findById(anyString());
    }

    @Test
    void testUpdateRoomTitle_EmptyTitle_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                roomService.updateRoomTitle("room123", "   ")
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title cannot be null or empty");

        verify(roomRepository, never()).findById(anyString());
    }

    @Test
    void testUpdateRoomTitle_TitleTooLong_ThrowsException() {
        // Given
        String longTitle = "a".repeat(256);

        // When/Then
        assertThatThrownBy(() ->
                roomService.updateRoomTitle("room123", longTitle)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed 255 characters");

        verify(roomRepository, never()).findById(anyString());
    }

    // ===== Update Privacy Mode Tests =====

    @Test
    void testUpdatePrivacyMode_ValidInput_UpdatesMode() {
        // Given
        String roomId = "room123";
        Room existingRoom = createTestRoom(roomId, "Test Room");
        existingRoom.privacyMode = PrivacyMode.PUBLIC;

        when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(existingRoom));
        when(roomRepository.persist(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            return Uni.createFrom().item(room);
        });

        // When
        Room result = roomService.updatePrivacyMode(roomId, PrivacyMode.INVITE_ONLY)
                .await().indefinitely();

        // Then
        assertThat(result.privacyMode).isEqualTo(PrivacyMode.INVITE_ONLY);
        verify(roomRepository).findById(roomId);
        verify(roomRepository).persist(any(Room.class));
    }

    @Test
    void testUpdatePrivacyMode_NullMode_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                roomService.updatePrivacyMode("room123", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Privacy mode cannot be null");

        verify(roomRepository, never()).findById(anyString());
    }

    // ===== Delete Room Tests =====

    @Test
    void testDeleteRoom_ValidRoom_SoftDeletes() {
        // Given
        String roomId = "room123";
        Room existingRoom = createTestRoom(roomId, "Test Room");

        when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(existingRoom));
        when(roomRepository.persist(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            return Uni.createFrom().item(room);
        });

        // When
        Room result = roomService.deleteRoom(roomId)
                .await().indefinitely();

        // Then
        assertThat(result.deletedAt).isNotNull();
        verify(roomRepository).findById(roomId);
        verify(roomRepository).persist(any(Room.class));
    }

    @Test
    void testDeleteRoom_RoomNotFound_ThrowsException() {
        // Given
        when(roomRepository.findById(anyString())).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                roomService.deleteRoom("nonexistent")
                        .await().indefinitely()
        )
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository).findById("nonexistent");
        verify(roomRepository, never()).persist(any(Room.class));
    }

    @Test
    void testDeleteRoom_AlreadyDeleted_ThrowsException() {
        // Given
        String roomId = "deleted123";
        Room deletedRoom = createTestRoom(roomId, "Deleted Room");
        deletedRoom.deletedAt = Instant.now();

        when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(deletedRoom));

        // When/Then
        assertThatThrownBy(() ->
                roomService.deleteRoom(roomId)
                        .await().indefinitely()
        )
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository).findById(roomId);
        verify(roomRepository, never()).persist(any(Room.class));
    }

    // ===== Find By ID Tests =====

    @Test
    void testFindById_ExistingRoom_ReturnsRoom() {
        // Given
        String roomId = "room123";
        Room existingRoom = createTestRoom(roomId, "Test Room");

        when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(existingRoom));

        // When
        Room result = roomService.findById(roomId)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.roomId).isEqualTo(roomId);
        assertThat(result.title).isEqualTo("Test Room");
        verify(roomRepository).findById(roomId);
    }

    @Test
    void testFindById_NonExistentRoom_ThrowsException() {
        // Given
        when(roomRepository.findById(anyString())).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                roomService.findById("nonexistent")
                        .await().indefinitely()
        )
                .isInstanceOf(RoomNotFoundException.class)
                .hasMessageContaining("nonexistent");

        verify(roomRepository).findById("nonexistent");
    }

    @Test
    void testFindById_DeletedRoom_ThrowsException() {
        // Given
        String roomId = "deleted123";
        Room deletedRoom = createTestRoom(roomId, "Deleted Room");
        deletedRoom.deletedAt = Instant.now();

        when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(deletedRoom));

        // When/Then
        assertThatThrownBy(() ->
                roomService.findById(roomId)
                        .await().indefinitely()
        )
                .isInstanceOf(RoomNotFoundException.class)
                .hasMessageContaining(roomId);

        verify(roomRepository).findById(roomId);
    }

    // ===== Find By Owner ID Tests =====

    @Test
    void testFindByOwnerId_ReturnsRooms() {
        // Given
        UUID ownerId = UUID.randomUUID();
        List<Room> rooms = Arrays.asList(
                createTestRoom("room1", "Room 1"),
                createTestRoom("room2", "Room 2")
        );

        when(roomRepository.findActiveByOwnerId(ownerId)).thenReturn(Uni.createFrom().item(rooms));

        // When
        List<Room> result = roomService.findByOwnerId(ownerId)
                .collect().asList()
                .await().indefinitely();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).roomId).isEqualTo("room1");
        assertThat(result.get(1).roomId).isEqualTo("room2");
        verify(roomRepository).findActiveByOwnerId(ownerId);
    }

    @Test
    void testFindByOwnerId_NoRooms_ReturnsEmptyList() {
        // Given
        UUID ownerId = UUID.randomUUID();
        when(roomRepository.findActiveByOwnerId(ownerId)).thenReturn(Uni.createFrom().item(List.of()));

        // When
        List<Room> result = roomService.findByOwnerId(ownerId)
                .collect().asList()
                .await().indefinitely();

        // Then
        assertThat(result).isEmpty();
        verify(roomRepository).findActiveByOwnerId(ownerId);
    }

    // ===== Get Room Config Tests =====

    @Test
    void testGetRoomConfig_ValidRoom_ReturnsConfig() throws JsonProcessingException {
        // Given
        String roomId = "room123";
        String configJson = "{\"deckType\":\"FIBONACCI\",\"timerEnabled\":true}";
        Room existingRoom = createTestRoom(roomId, "Test Room");
        existingRoom.config = configJson;

        when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(existingRoom));
        when(objectMapper.readValue(configJson, RoomConfig.class)).thenReturn(testConfig);

        // When
        RoomConfig result = roomService.getRoomConfig(roomId)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(testConfig);
        verify(roomRepository).findById(roomId);
        verify(objectMapper).readValue(configJson, RoomConfig.class);
    }

    @Test
    void testGetRoomConfig_RoomNotFound_ThrowsException() {
        // Given
        when(roomRepository.findById(anyString())).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                roomService.getRoomConfig("nonexistent")
                        .await().indefinitely()
        )
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomRepository).findById("nonexistent");
    }

    @Test
    void testGetRoomConfig_DeserializationFailure_ThrowsRuntimeException() throws JsonProcessingException {
        // Given
        String roomId = "room123";
        Room existingRoom = createTestRoom(roomId, "Test Room");
        existingRoom.config = "invalid json";

        when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(existingRoom));
        when(objectMapper.readValue(anyString(), eq(RoomConfig.class)))
                .thenThrow(new JsonProcessingException("Deserialization error") {});

        // When/Then
        assertThatThrownBy(() ->
                roomService.getRoomConfig(roomId)
                        .await().indefinitely()
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize room configuration");

        verify(roomRepository).findById(roomId);
    }

    // ===== Helper Methods =====

    private Room createTestRoom(String roomId, String title) {
        Room room = new Room();
        room.roomId = roomId;
        room.title = title;
        room.privacyMode = PrivacyMode.PUBLIC;
        room.config = "{}";
        room.createdAt = Instant.now();
        room.lastActiveAt = Instant.now();
        room.deletedAt = null;
        return room;
    }
}
