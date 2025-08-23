package com.terrencecurran.planningpoker.websocket.message;

public class JoinRoomMessage extends BaseMessage {
    public String username;
    
    public JoinRoomMessage() {
        this.type = "JOIN_ROOM";
    }
}