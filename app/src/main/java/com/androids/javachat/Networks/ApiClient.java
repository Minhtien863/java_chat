package com.androids.javachat.Networks;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static Retrofit retrofit;
    private static final String BASE_URL = "https://fcm.googleapis.com/";

    public static ApiService getApiService(String accessToken) {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit.newBuilder()
                .client(new okhttp3.OkHttpClient.Builder()
                        .addInterceptor(chain -> {
                            okhttp3.Request original = chain.request();
                            okhttp3.Request request = original.newBuilder()
                                    .header("Authorization", "Bearer " + accessToken)
                                    .build();
                            return chain.proceed(request);
                        })
                        .build())
                .build()
                .create(ApiService.class);
    }
}
