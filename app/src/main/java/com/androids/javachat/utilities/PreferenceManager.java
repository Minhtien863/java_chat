package com.androids.javachat.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.androids.javachat.R;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;

public class PreferenceManager {
    private final SharedPreferences sharedPreferences;
    private static final String KEY_FCM_TOKEN = "fcm_token";
    private static final String KEY_TOKEN_EXPIRATION = "token_expiration";
    private GoogleCredentials googleCredentials;

    public PreferenceManager(Context context) {
        sharedPreferences = context.getSharedPreferences(Constant.KEY_PREFERENCE_NAME, Context.MODE_PRIVATE);
        initGoogleCredentials(context);
    }

    private void initGoogleCredentials(Context context) {
        try {
            InputStream serviceAccountStream = context.getResources().openRawResource(R.raw.service_account); // Thay bằng tên file JSON của bạn
            googleCredentials = GoogleCredentials
                    .fromStream(serviceAccountStream)
                    .createScoped(Arrays.asList("https://www.googleapis.com/auth/firebase.messaging"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void putBoolean(String key, Boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    public boolean getBoolean(String key) {
        return sharedPreferences.getBoolean(key, false);
    }

    public void putString(String key, String value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    public String getString(String key) {
        return sharedPreferences.getString(key, null);
    }

    public void clear() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }

    // Trong saveFcmToken
    public void saveFcmToken(String token, long expirationTime) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_FCM_TOKEN, token);
        editor.putLong(KEY_TOKEN_EXPIRATION, expirationTime);
        editor.apply();
        Log.d("FCM_TOKEN", "Token saved: " + token + ", Expires at: " + new Date(expirationTime));
    }

    // Trong getFcmToken
    public String getFcmToken() {
        String token = sharedPreferences.getString(KEY_FCM_TOKEN, null);
        long expirationTime = sharedPreferences.getLong(KEY_TOKEN_EXPIRATION, 0);
        if (token != null && System.currentTimeMillis() < expirationTime) {
            Log.d("FCM_TOKEN", "Token retrieved: " + token + ", Still valid");
            return token;
        }
        Log.d("FCM_TOKEN", "Token expired or not found");
        return null;
    }

    // Trong refreshFcmToken
    public void refreshFcmToken(OnTokenRefreshListener listener) {
        if (googleCredentials != null) {
            new Thread(() -> {
                try {
                    googleCredentials.refreshIfExpired();
                    String newToken = googleCredentials.getAccessToken().getTokenValue();
                    long expirationTime = googleCredentials.getAccessToken().getExpirationTime().getTime();
                    saveFcmToken(newToken, expirationTime);
                    Log.d("FCM_TOKEN", "Token refreshed: " + newToken);
                    listener.onTokenRefreshed(newToken);
                } catch (IOException e) {
                    Log.e("FCM_TOKEN", "Failed to refresh token: " + e.getMessage());
                    listener.onTokenRefreshFailed(e);
                }
            }).start();
        } else {
            Log.e("FCM_TOKEN", "Google Credentials not initialized");
            listener.onTokenRefreshFailed(new IOException("Google Credentials not initialized"));
        }
    }

    // Interface để callback khi làm mới token
    public interface OnTokenRefreshListener {
        void onTokenRefreshed(String token);
        void onTokenRefreshFailed(Exception e);
    }
}