package com.androids.javachat.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.androids.javachat.R;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;

public class PreferenceManager {
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private static final String KEY_FCM_TOKEN = "fcm_token";
    private static final String KEY_TOKEN_EXPIRATION = "token_expiration";
    private GoogleCredentials googleCredentials;

    public PreferenceManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    Constant.KEY_PREFERENCE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            editor = sharedPreferences.edit();
            Log.d("PreferenceManager", "Initialized EncryptedSharedPreferences");
        } catch (Exception e) {
            Log.e("PreferenceManager", "Failed to initialize EncryptedSharedPreferences: " + e.getMessage());
            // Fallback to regular SharedPreferences
            sharedPreferences = context.getSharedPreferences(Constant.KEY_PREFERENCE_NAME, Context.MODE_PRIVATE);
            editor = sharedPreferences.edit();
            Log.d("PreferenceManager", "Fallback to regular SharedPreferences");
        }
        initGoogleCredentials(context);
    }

    private void initGoogleCredentials(Context context) {
        try {
            InputStream serviceAccountStream = context.getResources().openRawResource(R.raw.account_service);
            googleCredentials = GoogleCredentials
                    .fromStream(serviceAccountStream)
                    .createScoped(Arrays.asList("https://www.googleapis.com/auth/firebase.messaging"));
            Log.d("PreferenceManager", "Initialized GoogleCredentials");
        } catch (IOException e) {
            Log.e("PreferenceManager", "Failed to initialize GoogleCredentials: " + e.getMessage());
        }
    }

    public void putBoolean(String key, Boolean value) {
        editor.putBoolean(key, value).apply();
    }

    public boolean getBoolean(String key) {
        return sharedPreferences.getBoolean(key, false);
    }

    public void putString(String key, String value) {
        editor.putString(key, value).apply();
    }

    public String getString(String key) {
        return sharedPreferences.getString(key, null);
    }

    public void clear() {
        editor.clear().apply();
    }

    public void saveFcmToken(String token, long expirationTime) {
        editor.putString(KEY_FCM_TOKEN, token);
        editor.putLong(KEY_TOKEN_EXPIRATION, expirationTime);
        editor.apply();
        Log.d("FCM_TOKEN", "Token saved: " + token + ", Expires at: " + new Date(expirationTime));
    }

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

    public interface OnTokenRefreshListener {
        void onTokenRefreshed(String token);
        void onTokenRefreshFailed(Exception e);
    }
}