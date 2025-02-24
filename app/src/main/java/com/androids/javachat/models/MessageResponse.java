package com.androids.javachat.models;


import com.google.gson.annotations.SerializedName;

public class MessageResponse {

    @SerializedName("name")
    private String messageId;

    public String getMessageId() {
        return messageId;
    }
}
