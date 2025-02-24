package com.androids.javachat.models;

import com.google.gson.annotations.SerializedName;

public class MessageRequest {

    @SerializedName("message")
    private Message message;

    public MessageRequest(Message message) {
        this.message = message;
    }

    public static class Message {
        @SerializedName("token")
        private String token;

        @SerializedName("notification")
        private Notification notification;

        public Message(String token, Notification notification) {
            this.token = token;
            this.notification = notification;
        }
    }

    public static class Notification {
        @SerializedName("title")
        private String title;

        @SerializedName("body")
        private String body;

        public Notification(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }
}