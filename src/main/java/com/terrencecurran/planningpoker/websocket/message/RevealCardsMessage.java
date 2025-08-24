package com.terrencecurran.planningpoker.websocket.message;

public class RevealCardsMessage extends BaseMessage {
    public boolean reveal;
    
    public RevealCardsMessage() {
        this.type = "REVEAL_CARDS";
    }
}