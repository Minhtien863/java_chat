package com.androids.javachat.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import android.Manifest;

import com.androids.javachat.adapter.RecentConversationsAdapter;
import com.androids.javachat.databinding.ActivityMainBinding;
import com.androids.javachat.listener.ConversionListener;
import com.androids.javachat.models.ChatMessage;
import com.androids.javachat.models.User;
import com.androids.javachat.utilities.Constant;
import com.androids.javachat.utilities.PreferenceManager;
import com.androids.javachat.utilities.SpaceItemDecoration;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;

public class MainActivity extends BaseActivity implements ConversionListener {

    private static final int NOTIFICATION_PERMISSION_CODE = 1001;
    private ActivityMainBinding binding;
    private PreferenceManager preferenceManager;
    private List<ChatMessage> conversations;
    private RecentConversationsAdapter conversationsAdapter;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        preferenceManager = new PreferenceManager(getApplicationContext());
        if (auth.getCurrentUser() == null || preferenceManager.getString(Constant.KEY_USER_ID) == null) {
            Log.w("MainActivity", "Invalid session: user not logged in");
            signOut();
            return;
        }
        checkAuthToken();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        init();
        loadUserDetail();
        getToken();
        fetchAesKey();
        setListeners();
        listenConversion();
    }

    private void checkAuthToken() {
        auth.getCurrentUser().getIdToken(false).addOnSuccessListener(result -> {
            Log.d("MainActivity", "Auth token valid until: " + result.getExpirationTimestamp());
        }).addOnFailureListener(e -> {
            Log.e("MainActivity", "Auth token invalid: " + e.getMessage());
            showToast("Phiên đăng nhập hết hạn, vui lòng đăng nhập lại");
            signOut();
        });
    }

    private void init() {
        conversations = new ArrayList<>();
        conversationsAdapter = new RecentConversationsAdapter(conversations, this);
        binding.conversationsRecyclerView.setAdapter(conversationsAdapter);
        int spacingInPixels = (int) (16 * getResources().getDisplayMetrics().density);
        binding.conversationsRecyclerView.addItemDecoration(new SpaceItemDecoration(spacingInPixels));
        db = FirebaseFirestore.getInstance();
    }

    private void fetchAesKey() {
        db.collection("config").document("sharedAesKey").get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String aesKey = documentSnapshot.getString("key");
                        if (aesKey != null) {
                            preferenceManager.putString("AES_KEY", aesKey);
                            Log.d("MainActivity", "AES key fetched and stored: " + aesKey);
                        } else {
                            Log.e("MainActivity", "AES key is null");
                            showToast("Failed to load encryption key");
                        }
                    } else {
                        Log.e("MainActivity", "AES key document does not exist");
                        showToast("Encryption key not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("MainActivity", "Failed to fetch AES key: " + e.getMessage());
                    showToast("Failed to load encryption key");
                });
    }

    private String decodeMessage(String message) {
        if (message == null) return "";
        return message
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\u0022", "\"")
                .replace("\\u0027", "'");
    }

    private String decryptMessage(String encryptedMessage) {
        if (encryptedMessage == null || encryptedMessage.isEmpty()) {
            Log.w("MainActivity", "Encrypted message is null or empty");
            return "";
        }
        try {
            String aesKey = preferenceManager.getString("AES_KEY");
            if (aesKey == null) {
                Log.e("MainActivity", "AES key not found for decryption");
                return encryptedMessage;
            }
            Log.d("MainActivity", "Attempting to decrypt message: " + encryptedMessage);
            byte[] keyBytes = Base64.decode(aesKey, Base64.DEFAULT);
            byte[] encryptedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT);
            if (encryptedBytes.length < 16) {
                Log.e("MainActivity", "Invalid encrypted message: too short for IV");
                return encryptedMessage;
            }
            byte[] iv = new byte[16];
            byte[] ciphertext = new byte[encryptedBytes.length - 16];
            System.arraycopy(encryptedBytes, 0, iv, 0, 16);
            System.arraycopy(encryptedBytes, 16, ciphertext, 0, encryptedBytes.length - 16);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(iv));
            byte[] decryptedBytes = cipher.doFinal(ciphertext);
            String decryptedMessage = new String(decryptedBytes, StandardCharsets.UTF_8);
            Log.d("MainActivity", "Decryption successful: " + decryptedMessage);
            return decryptedMessage;
        } catch (Exception e) {
            Log.e("MainActivity", "Decryption failed: " + e.getMessage() + ", Input: " + encryptedMessage);
            return encryptedMessage;
        }
    }

    private void setListeners() {
        binding.imageSignOut.setOnClickListener(v -> signOut());
        binding.fabNewChat.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), UsersActivity.class)));
    }

    private void loadUserDetail() {
        binding.txtName.setText(preferenceManager.getString(Constant.KEY_NAME));
        byte[] bytes = Base64.decode(preferenceManager.getString(Constant.KEY_IMAGE), Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        binding.imageProfile.setImageBitmap(bitmap);
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void listenConversion() {
        Log.d("MainActivity", "Listening to conversations for user: " + preferenceManager.getString(Constant.KEY_USER_ID));
        db.collection(Constant.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constant.KEY_SENDER_ID, preferenceManager.getString(Constant.KEY_USER_ID))
                .addSnapshotListener(eventListener);
        db.collection(Constant.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constant.KEY_RECEIVER_ID, preferenceManager.getString(Constant.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            Log.e("MainActivity", "Firestore listener error: " + error.getMessage());
            return;
        }
        if (value != null) {
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    String senderId = documentChange.getDocument().getString(Constant.KEY_SENDER_ID);
                    String receiverId = documentChange.getDocument().getString(Constant.KEY_RECEIVER_ID);
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = senderId;
                    chatMessage.receiverId = receiverId;
                    if (preferenceManager.getString(Constant.KEY_USER_ID).equals(senderId)) {
                        chatMessage.conversionImg = documentChange.getDocument().getString(Constant.KEY_RECEIVER_IMG);
                        chatMessage.conversionName = documentChange.getDocument().getString(Constant.KEY_RECEIVER_NAME);
                        chatMessage.conversionId = documentChange.getDocument().getString(Constant.KEY_RECEIVER_ID);
                    } else {
                        chatMessage.conversionImg = documentChange.getDocument().getString(Constant.KEY_SENDER_IMG);
                        chatMessage.conversionName = documentChange.getDocument().getString(Constant.KEY_SENDER_NAME);
                        chatMessage.conversionId = documentChange.getDocument().getString(Constant.KEY_SENDER_ID);
                    }
                    String encryptedMessage = documentChange.getDocument().getString(Constant.KEY_LAST_MESSAGE);
                    String decryptedMessage = decryptMessage(encryptedMessage);
                    chatMessage.message = decodeMessage(decryptedMessage);
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constant.KEY_TIMESTAMP);
                    conversations.add(chatMessage);
                } else if (documentChange.getType() == DocumentChange.Type.MODIFIED) {
                    for (int i = 0; i < conversations.size(); i++) {
                        String senderId = documentChange.getDocument().getString(Constant.KEY_SENDER_ID);
                        String receiverId = documentChange.getDocument().getString(Constant.KEY_RECEIVER_ID);
                        if (conversations.get(i).senderId.equals(senderId) && conversations.get(i).receiverId.equals(receiverId)) {
                            String encryptedMessage = documentChange.getDocument().getString(Constant.KEY_LAST_MESSAGE);
                            String decryptedMessage = decryptMessage(encryptedMessage);
                            conversations.get(i).message = decodeMessage(decryptedMessage);
                            conversations.get(i).dateObject = documentChange.getDocument().getDate(Constant.KEY_TIMESTAMP);
                            break;
                        }
                    }
                }
            }
            Collections.sort(conversations, (obj1, ojb2) -> ojb2.dateObject.compareTo(obj1.dateObject));
            conversationsAdapter.notifyDataSetChanged();
            binding.conversationsRecyclerView.smoothScrollToPosition(0);
            binding.conversationsRecyclerView.setVisibility(View.VISIBLE);
            binding.progBar.setVisibility(View.GONE);
        }
    };

    private void getToken() {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            String currentToken = preferenceManager.getFcmToken();
            if (!token.equals(currentToken)) {
                updateToken(token);
            }
            Log.d("MainActivity", "FCM token fetched: " + token);
        }).addOnFailureListener(e -> {
            Log.e("MainActivity", "Failed to fetch FCM token: " + e.getMessage());
        });
    }

    private void updateToken(String token) {
        preferenceManager.saveFcmToken(token, System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000); // 7 days
        String userId = preferenceManager.getString(Constant.KEY_USER_ID);
        if (userId != null) {
            DocumentReference documentReference = db.collection(Constant.KEY_COLLECTION_USERS)
                    .document(userId);
            documentReference.update(Constant.KEY_FCM_TOKEN, token)
                    .addOnSuccessListener(unused -> {
                        showToast("Token updated successfully");
                    })
                    .addOnFailureListener(e -> {
                        showToast("Unable to update token: " + e.getMessage());
                    });
        } else {
            showToast("User ID not found");
        }
    }

    private void signOut() {
        if (auth.getCurrentUser() == null) {
            Log.w("MainActivity", "Already signed out, redirecting to SignInActivity");
            redirectToSignIn();
            return;
        }

        String userId = preferenceManager.getString(Constant.KEY_USER_ID);
        Log.d("MainActivity", "User signing out: " + userId);

        // Log sign out event
        HashMap<String, Object> log = new HashMap<>();
        log.put("userId", userId != null ? userId : "unknown");
        log.put("action", "sign_out");
        log.put("timestamp", FieldValue.serverTimestamp());
        log.put("deviceInfo", Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");
        db.collection("logs").add(log)
                .addOnSuccessListener(documentReference -> {
                    Log.d("MainActivity", "Sign out logged successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e("MainActivity", "Failed to log sign out: " + e.getMessage());
                });

        // Proceed with sign out
        auth.signOut();
        if (userId != null) {
            DocumentReference documentReference = db.collection(Constant.KEY_COLLECTION_USERS)
                    .document(userId);
            HashMap<String, Object> updates = new HashMap<>();
            updates.put(Constant.KEY_FCM_TOKEN, FieldValue.delete());
            updates.put(Constant.KEY_AVAILABILITY, 0);
            documentReference.update(updates)
                    .addOnSuccessListener(unused -> {
                        Log.d("MainActivity", "User data updated successfully");
                    })
                    .addOnFailureListener(e -> {
                        Log.e("MainActivity", "Failed to update user data: " + e.getMessage());
                    });
        }

        // Clear preferences and redirect
        preferenceManager.clear();
        preferenceManager.putBoolean(Constant.KEY_SIGNED_IN, false);
        redirectToSignIn();
    }

    private void redirectToSignIn() {
        Intent intent = new Intent(getApplicationContext(), SignInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onConversionClicked(User user) {
        if (user == null || user.id == null || user.name == null) {
            Log.e("MainActivity", "Invalid user: " + (user == null ? "null" : "id or name missing"));
            showToast("Không thể mở hội thoại: Dữ liệu người dùng không hợp lệ");
            return;
        }
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constant.KEY_USER, user);
        startActivity(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_CODE);
        }
    }
}