package com.terrencecurran.planningpoker.websocket.message;

public class ToggleObserverMessage extends BaseMessage {
    public ToggleObserverMessage() {
        this.type = "TOGGLE_OBSERVER";
    }
}