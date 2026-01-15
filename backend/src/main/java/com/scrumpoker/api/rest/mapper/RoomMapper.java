package com.scrumpoker.api.rest.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.api.rest.dto.RoomConfigDTO;
import com.scrumpoker.api.rest.dto.RoomDTO;
import com.scrumpoker.domain.room.DeckType;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.RoomConfig;
import jakarta.inject.Inject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;

/**
 * Mapper for converting between Room entities and RoomDTOs.
 * Handles JSONB serialization/deserialization for room configuration.
 */
@Mapper(componentModel = "cdi", imports = {Collections.class})
public abstract class RoomMapper {

    @Inject
    ObjectMapper objectMapper;

    @Mapping(target = "ownerId", expression = "java(room.owner != null ? room.owner.userId : null)")
    @Mapping(target = "organizationId", expression = "java(room.organization != null ? room.organization.orgId : null)")
    @Mapping(target = "privacyMode", expression = "java(room.privacyMode != null ? room.privacyMode.name() : null)")
    @Mapping(target = "config", expression = "java(deserializeConfigToDTO(room.config))")
    @Mapping(target = "participants", expression = "java(Collections.emptyList())")
    public abstract RoomDTO toDTO(Room room);

    /**
     * Converts RoomConfigDTO to RoomConfig domain object.
     *
     * @param dto The configuration DTO
     * @return RoomConfig domain object
     */
    public RoomConfig toConfig(RoomConfigDTO dto) {
        if (dto == null) {
            return new RoomConfig();
        }

        RoomConfig config = new RoomConfig();
        if (dto.deckType != null) {
            config.setDeckType(DeckType.fromValue(dto.deckType));
        }
        config.setTimerEnabled(dto.timerEnabled != null ? dto.timerEnabled : false);
        config.setTimerDurationSeconds(dto.timerDurationSeconds != null ? dto.timerDurationSeconds : 60);
        config.setRevealBehavior(dto.revealBehavior != null ? dto.revealBehavior : "MANUAL");
        config.setAllowObservers(dto.allowObservers != null ? dto.allowObservers : true);
        config.setAllowAnonymousVoters(dto.allowAnonymousVoters != null ? dto.allowAnonymousVoters : true);
        config.setCustomDeck(dto.customDeck);

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
        dto.deckType = config.getDeckType() != null ? config.getDeckType().getJsonValue() : null;
        dto.timerEnabled = config.isTimerEnabled();
        dto.timerDurationSeconds = config.getTimerDurationSeconds();
        dto.revealBehavior = config.getRevealBehavior();
        dto.allowObservers = config.isAllowObservers();
        dto.allowAnonymousVoters = config.isAllowAnonymousVoters();
        dto.customDeck = config.getCustomDeck();

        return dto;
    }

    /**
     * Deserializes JSONB config string to RoomConfigDTO.
     *
     * @param configJson The JSON string from database
     * @return RoomConfigDTO or null if deserialization fails
     */
    protected RoomConfigDTO deserializeConfigToDTO(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return null;
        }

        try {
            RoomConfig config = objectMapper.readValue(configJson, RoomConfig.class);
            return toConfigDTO(config);
        } catch (JsonProcessingException e) {
            // Log error but don't fail the entire mapping
            System.err.println("Failed to deserialize room configuration: " + e.getMessage());
            return null;
        }
    }
}
