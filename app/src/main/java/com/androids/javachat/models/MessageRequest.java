package com.androids.javachat.models;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;

public class MessageRequest {

    @SerializedName("message")
    private Message message;

    public MessageRequest(Message message) {
        this.message = message;
    }

    public Message getMessage() {
        return message;
    }

    public static class Message {
        @SerializedName("token")
        private String token;

        @SerializedName("notification")
        private Notification notification;

        @SerializedName("data")
        private HashMap<String, String> data;

        public Message(String token, Notification notification, HashMap<String, String> data) {
            this.token = token;
            this.notification = notification;
            this.data = data;
        }

        public Message(String token, HashMap<String, String> data) {
            this.token = token;
            this.data = data;
            this.notification = null;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Notification getNotification() {
            return notification;
        }

        public void setNotification(Notification notification) {
            this.notification = notification;
        }

        public HashMap<String, String> getData() {
            return data;
        }

        public void setData(HashMap<String, String> data) {
            this.data = data;
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

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }
}