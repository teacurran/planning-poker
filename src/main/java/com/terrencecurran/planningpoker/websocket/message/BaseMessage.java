package com.terrencecurran.planningpoker.websocket.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = JoinRoomMessage.class, name = "JOIN_ROOM"),
    @JsonSubTypes.Type(value = VoteMessage.class, name = "VOTE"),
    @JsonSubTypes.Type(value = RoomStateMessage.class, name = "ROOM_STATE"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR")
})
public class BaseMessage {
    public String type;
}