package com.looktech.plutus.enums;

public enum SourceType {
    CHAT("Chat Service"),
    SIGN_UP("Sign Up Bonus"),
    INVITATION("Invitation Bonus"),
    ACTIVITY("Activity Bonus"),
    SYSTEM("System Grant");

    private final String description;

    SourceType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
} 