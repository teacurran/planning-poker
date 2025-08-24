package com.terrencecurran.planningpoker.websocket.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = JoinRoomMessage.class, name = "JOIN_ROOM"),
    @JsonSubTypes.Type(value = VoteMessage.class, name = "VOTE"),
    @JsonSubTypes.Type(value = RoomStateMessage.class, name = "ROOM_STATE"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR"),
    @JsonSubTypes.Type(value = RevealCardsMessage.class, name = "REVEAL_CARDS"),
    @JsonSubTypes.Type(value = HideCardsMessage.class, name = "HIDE_CARDS"),
    @JsonSubTypes.Type(value = ResetVotesMessage.class, name = "RESET_VOTES"),
    @JsonSubTypes.Type(value = ToggleObserverMessage.class, name = "TOGGLE_OBSERVER")
})
public class BaseMessage {
    public String type;
}