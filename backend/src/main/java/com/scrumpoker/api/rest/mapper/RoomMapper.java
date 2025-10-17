package com.scrumpoker.api.rest.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.api.rest.dto.RoomConfigDTO;
import com.scrumpoker.api.rest.dto.RoomDTO;
import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.RoomConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;

/**
 * Mapper for converting between Room entities and RoomDTOs.
 * Handles JSONB serialization/deserialization for room configuration.
 */
@ApplicationScoped
public class RoomMapper {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Converts a Room entity to RoomDTO for REST API responses.
     *
     * @param room The room entity
     * @return RoomDTO with all fields mapped
     */
    public RoomDTO toDTO(Room room) {
        if (room == null) {
            return null;
        }

        RoomDTO dto = new RoomDTO();
        dto.roomId = room.roomId;
        dto.ownerId = room.owner != null ? room.owner.userId : null;
        dto.organizationId = room.organization != null ? room.organization.orgId : null;
        dto.title = room.title;
        dto.privacyMode = room.privacyMode != null ? room.privacyMode.name() : null;
        dto.config = deserializeConfigToDTO(room.config);
        dto.createdAt = room.createdAt;
        dto.lastActiveAt = room.lastActiveAt;
        dto.participants = Collections.emptyList(); // Will be populated by WebSocket state in future iterations

        return dto;
    }

    /**
     * Converts RoomConfigDTO to RoomConfig domain object.
     *
     * @param dto The configuration DTO
     * @return RoomConfig domain object
     */
    public RoomConfig toConfig(RoomConfigDTO dto) {
        if (dto == null) {
            return new RoomConfig(); // Return default config
        }

        RoomConfig config = new RoomConfig();
        config.setDeckType(dto.deckType != null ? dto.deckType : "FIBONACCI");
        config.setTimerEnabled(dto.timerEnabled != null ? dto.timerEnabled : false);
        config.setTimerDurationSeconds(dto.timerDurationSeconds != null ? dto.timerDurationSeconds : 60);
        config.setRevealBehavior(dto.revealBehavior != null ? dto.revealBehavior : "MANUAL");
        config.setAllowObservers(dto.allowObservers != null ? dto.allowObservers : true);

        return config;
    }

    /**
     * Converts RoomConfig domain object to RoomConfigDTO.
     *
     * @param config The configuration domain object
     * @return RoomConfigDTO for API responses
     */
    public RoomConfigDTO toConfigDTO(RoomConfig config) {
        if (config == null) {
            return null;
        }

        RoomConfigDTO dto = new RoomConfigDTO();
        dto.deckType = config.getDeckType();
        dto.timerEnabled = config.isTimerEnabled();
        dto.timerDurationSeconds = config.getTimerDurationSeconds();
        dto.revealBehavior = config.getRevealBehavior();
        dto.allowObservers = config.isAllowObservers();
        dto.customDeck = null; // Not yet implemented in domain model
        dto.allowAnonymousVoters = true; // Default for now

        return dto;
    }

    /**
     * Deserializes JSONB config string to RoomConfigDTO.
     *
     * @param configJson The JSON string from database
     * @return RoomConfigDTO or null if deserialization fails
     */
    private RoomConfigDTO deserializeConfigToDTO(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return null;
        }

        try {
            // First deserialize to RoomConfig domain object
            RoomConfig config = objectMapper.readValue(configJson, RoomConfig.class);
            // Then convert to DTO
            return toConfigDTO(config);
        } catch (JsonProcessingException e) {
            // Log error but don't fail the entire mapping
            System.err.println("Failed to deserialize room configuration: " + e.getMessage());
            return null;
        }
    }
}
