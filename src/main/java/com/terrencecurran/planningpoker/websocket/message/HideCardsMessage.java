package com.terrencecurran.planningpoker.websocket.message;

public class HideCardsMessage extends BaseMessage {
    
    public HideCardsMessage() {
        this.type = "HIDE_CARDS";
    }
}