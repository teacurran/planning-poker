package com.terrencecurran.planningpoker.websocket.message;

import com.terrencecurran.planningpoker.dto.RoomState;

public class RoomStateMessage extends BaseMessage {
    public RoomState roomState;
    
    public RoomStateMessage() {
        this.type = "ROOM_STATE";
    }
    
    public RoomStateMessage(RoomState roomState) {
        this.type = "ROOM_STATE";
        this.roomState = roomState;
    }
}