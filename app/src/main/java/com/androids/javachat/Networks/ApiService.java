package com.androids.javachat.Networks;

import com.androids.javachat.models.MessageRequest;
import com.androids.javachat.models.MessageResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface ApiService {
    @Headers("Content-Type: application/json")
    @POST("v1/projects/java-chat-app-f0fbb/messages:send")
    Call<MessageResponse> sendNotification(@Body MessageRequest body);
}