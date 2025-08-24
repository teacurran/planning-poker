package com.terrencecurran.planningpoker.websocket.message;

public class ResetVotesMessage extends BaseMessage {
    
    public ResetVotesMessage() {
        this.type = "RESET_VOTES";
    }
}