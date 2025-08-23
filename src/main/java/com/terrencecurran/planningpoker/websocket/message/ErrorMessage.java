package com.terrencecurran.planningpoker.websocket.message;

public class ErrorMessage extends BaseMessage {
    public String message;
    
    public ErrorMessage() {
        this.type = "ERROR";
    }
    
    public ErrorMessage(String message) {
        this.type = "ERROR";
        this.message = message;
    }
}