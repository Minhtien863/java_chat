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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PreferenceManager {
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private GoogleCredentials googleCredentials;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

    public String getAccessToken() throws IOException {
        if (googleCredentials == null) {
            throw new IOException("GoogleCredentials not initialized");
        }
        Future<String> future = executor.submit(() -> {
            try {
                googleCredentials.refreshIfExpired();
                return googleCredentials.getAccessToken().getTokenValue();
            } catch (IOException e) {
                Log.e("PreferenceManager", "Failed to refresh access token: " + e.getMessage());
                throw e;
            }
        });
        try {
            return future.get();
        } catch (Exception e) {
            Log.e("PreferenceManager", "Failed to get access token: " + e.getMessage());
            throw new IOException("Failed to get access token", e);
        }
    }

    public void saveDeviceFcmToken(String token) {
        editor.putString(Constant.DEVICE_FCM_TOKEN, token).apply();
        Log.d("FCM_TOKEN", "Device FCM token saved: " + token);
    }

    public String getDeviceFcmToken() {
        String token = sharedPreferences.getString(Constant.DEVICE_FCM_TOKEN, null);
        Log.d("FCM_TOKEN", "Device FCM token retrieved: " + (token != null ? token : "null"));
        return token;
    }
}