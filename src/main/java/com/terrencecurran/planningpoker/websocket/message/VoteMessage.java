package com.terrencecurran.planningpoker.websocket.message;

public class VoteMessage extends BaseMessage {
    public String value;
    
    public VoteMessage() {
        this.type = "VOTE";
    }
}