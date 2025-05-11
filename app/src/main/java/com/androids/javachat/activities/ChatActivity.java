package com.androids.javachat.activities;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.util.Log;
import android.widget.Toast;

import com.androids.javachat.adapter.ChatAdapter;
import com.androids.javachat.databinding.ActivityChatBinding;
import com.androids.javachat.models.ChatMessage;
import com.androids.javachat.models.MessageRequest;
import com.androids.javachat.models.MessageResponse;
import com.androids.javachat.models.User;
import com.androids.javachat.utilities.Constant;
import com.androids.javachat.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.androids.javachat.Networks.ApiClient;
import com.androids.javachat.Networks.ApiService;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {

    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String conversionId = null;
    private Boolean isReceiverOnline = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setListener();
        loadReceiverDetails();
        init();
        listenMessage();
    }

    private void init() {
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages, getBitmapFromEncodedString(receiverUser.image), preferenceManager.getString(Constant.KEY_USER_ID));
        binding.chatView.setAdapter(chatAdapter);
    }

    private void listenMessage() {
        db.collection(Constant.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constant.KEY_SENDER_ID, preferenceManager.getString(Constant.KEY_USER_ID))
                .whereEqualTo(Constant.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);
        db.collection(Constant.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constant.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constant.KEY_RECEIVER_ID, preferenceManager.getString(Constant.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private void logChatEvent(String userId, String action) {
        HashMap<String, Object> log = new HashMap<>();
        log.put("userId", userId != null ? userId : "unknown");
        log.put("action", action);
        log.put("receiverId", receiverUser != null ? receiverUser.id : "unknown");
        log.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        log.put("deviceInfo", android.os.Build.MODEL + " (Android " + android.os.Build.VERSION.RELEASE + ")");
        db.collection("logs").add(log)
                .addOnFailureListener(e -> Log.e("ChatActivity", "Failed to log chat event: " + e.getMessage()));
    }

    private boolean checkMessageRateLimit() {
        int messageCount = preferenceManager.getString(Constant.KEY_MESSAGE_COUNT) != null ?
                Integer.parseInt(preferenceManager.getString(Constant.KEY_MESSAGE_COUNT)) : 0;
        long lastMessageTime = preferenceManager.getString(Constant.KEY_MESSAGE_TIMESTAMP) != null ?
                Long.parseLong(preferenceManager.getString(Constant.KEY_MESSAGE_TIMESTAMP)) : 0;
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastMessageTime > Constant.RATE_LIMIT_WINDOW) {
            preferenceManager.putString(Constant.KEY_MESSAGE_COUNT, "0");
            preferenceManager.putString(Constant.KEY_MESSAGE_TIMESTAMP, String.valueOf(currentTime));
            return true;
        }

        if (messageCount >= Constant.MAX_MESSAGES_PER_MINUTE) {
            logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "rate_limit_exceeded");
            return false;
        }

        return true;
    }

    private void incrementMessageCount() {
        int messageCount = preferenceManager.getString(Constant.KEY_MESSAGE_COUNT) != null ?
                Integer.parseInt(preferenceManager.getString(Constant.KEY_MESSAGE_COUNT)) : 0;
        preferenceManager.putString(Constant.KEY_MESSAGE_COUNT, String.valueOf(messageCount + 1));
        preferenceManager.putString(Constant.KEY_MESSAGE_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            Log.e("ChatActivity", "Empty message");
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (message.length() > 1000) {
            Log.e("ChatActivity", "Message too long: " + message.length());
            Toast.makeText(this, "Message cannot exceed 1000 characters", Toast.LENGTH_SHORT).show();
            return null;
        }
        String sanitized = message
                .replace("<", "\\u003C")
                .replace(">", "\\u003E")
                .replace("\"", "\\u0022")
                .replace("'", "\\u0027");
        Log.d("ChatActivity", "Message sanitized: " + sanitized);
        return sanitized;
    }

    private String decodeMessage(String message) {
        if (message == null) return "";
        return message
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\u0022", "\"")
                .replace("\\u0027", "'");
    }

    //Mã hóa tin nhắn
    private String encryptMessage(String message) {
        if (message == null || message.isEmpty()) {
            Log.w("ChatActivity", "Message is null or empty");
            return "";
        }
        String aesKey = preferenceManager.getString("AES_KEY");
        if (aesKey == null) {
            Log.e("ChatActivity", "AES key not found for encryption");
            showToast("Lỗi: Không tìm thấy khóa mã hóa");
            return ""; // Trả về chuỗi rỗng thay vì message thô
        }
        try {
            byte[] keyBytes = Base64.decode(aesKey, Base64.DEFAULT);
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[16];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, encrypted, 0, iv.length);
            System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length);
            String encoded = Base64.encodeToString(encrypted, Base64.DEFAULT);
            Log.d("ChatActivity", "Encryption successful: " + encoded);
            return encoded;
        } catch (Exception e) {
            Log.e("ChatActivity", "Encryption failed: " + e.getMessage());
            showToast("Lỗi mã hóa tin nhắn");
            return ""; // Trả về chuỗi rỗng thay vì message thô
        }
    }

    private String decryptMessage(String encryptedMessage) {
        if (encryptedMessage == null || encryptedMessage.isEmpty()) return "";
        try {
            String aesKey = preferenceManager.getString("AES_KEY");
            if (aesKey == null) {
                Log.e("ChatActivity", "AES key not found for decryption");
                logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "decryption_failed");
                return encryptedMessage;
            }
            byte[] keyBytes = Base64.decode(aesKey, Base64.DEFAULT);
            byte[] encryptedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT);
            byte[] iv = new byte[16];
            byte[] ciphertext = new byte[encryptedBytes.length - 16];
            System.arraycopy(encryptedBytes, 0, iv, 0, 16);
            System.arraycopy(encryptedBytes, 16, ciphertext, 0, encryptedBytes.length - 16);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(iv));
            byte[] decryptedBytes = cipher.doFinal(ciphertext);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e("ChatActivity", "Decryption failed: " + e.getMessage());
            logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "decryption_failed");
            return "encryptedMessage";
        }
    }

    private void sendMessage() {
        if (!checkMessageRateLimit()) {
            Toast.makeText(this, "Bạn đã gửi quá nhiều tin nhắn, vui lòng thử lại sau 1 phút", Toast.LENGTH_SHORT).show();
            return;
        }

        String messageText = binding.inputMessage.getText().toString();
        String sanitizedMessage = sanitizeMessage(messageText);
        if (sanitizedMessage == null) {
            return;
        }
        String encryptedMessage = encryptMessage(sanitizedMessage);
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constant.KEY_SENDER_ID, preferenceManager.getString(Constant.KEY_USER_ID));
        message.put(Constant.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constant.KEY_MESSAGE, encryptedMessage);
        message.put(Constant.KEY_TIMESTAMP, new Date());
        db.collection(Constant.KEY_COLLECTION_CHAT).add(message).addOnSuccessListener(documentReference -> {
            incrementMessageCount();
            logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "message_sent");
            if (receiverUser.token == null) {
                fetchReceiverFcmTokenFromFirestoreAndSend(sanitizedMessage);
            } else {
                sendNotificationToReceiver(sanitizedMessage);
            }
        }).addOnFailureListener(e -> {
            Log.e("Firestore", "Failed to send message: " + e.getMessage());
            logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "message_send_failed");
        });
        binding.inputMessage.setText(null);
        if (conversionId != null) {
            updateConversion(encryptedMessage);
        } else {
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constant.KEY_SENDER_ID, preferenceManager.getString(Constant.KEY_USER_ID));
            conversion.put(Constant.KEY_SENDER_NAME, preferenceManager.getString(Constant.KEY_NAME));
            conversion.put(Constant.KEY_SENDER_IMG, preferenceManager.getString(Constant.KEY_IMAGE));
            conversion.put(Constant.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constant.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constant.KEY_RECEIVER_IMG, receiverUser.image);
            conversion.put(Constant.KEY_LAST_MESSAGE, encryptedMessage);
            conversion.put(Constant.KEY_TIMESTAMP, new Date());
            addConversion(conversion);
        }
    }

    private void fetchReceiverFcmTokenFromFirestoreAndSend(String sanitizedMessage) {
        db.collection(Constant.KEY_COLLECTION_USERS).document(receiverUser.id).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                DocumentSnapshot document = task.getResult();
                receiverUser.token = document.getString(Constant.KEY_FCM_TOKEN);
                if (receiverUser.token != null && !receiverUser.token.isEmpty()) {
                    sendNotificationToReceiver(sanitizedMessage);
                } else {
                    Log.e("FCM_TEST", "FCM token still not found for receiver after fetch");
                    logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "fcm_token_missing");
                }
            } else {
                Log.e("FCM_TEST", "Failed to fetch FCM token: " + task.getException());
                logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "fcm_fetch_failed");
            }
        });
    }

    private void sendNotificationToReceiver(String sanitizedMessage) {
        try {
            String accessToken = preferenceManager.getAccessToken();
            sendNotificationWithToken(accessToken, sanitizedMessage);
        } catch (IOException e) {
            Log.e("FCM_TEST", "Failed to get access token: " + e.getMessage());
            logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "fcm_access_token_failed");
        }
    }

    private void sendNotificationWithToken(String accessToken, String sanitizedMessage) {
        if (receiverUser.token == null || receiverUser.token.isEmpty()) {
            Log.e("FCM_TEST", "FCM token not found for receiver");
            logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "fcm_token_missing");
            return;
        }

        HashMap<String, String> data = new HashMap<>();
        data.put("title", "JavaChat");
        data.put("body", "Bạn có tin nhắn từ " + preferenceManager.getString(Constant.KEY_NAME));
        data.put("senderId", preferenceManager.getString(Constant.KEY_USER_ID));
        data.put("senderName", preferenceManager.getString(Constant.KEY_NAME));

        Log.d("FCM_TEST", "Receiver token: " + receiverUser.token);

        HashMap<String, Object> message = new HashMap<>();
        message.put("token", receiverUser.token);
        message.put("data", data);
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("message", message);

        String jsonRequest = new Gson().toJson(requestMap);
        Log.d("FCM_TEST", "Sending request: " + jsonRequest);

        MessageRequest request = new MessageRequest(new MessageRequest.Message(receiverUser.token, data));

        ApiService apiService = ApiClient.getApiService(accessToken);
        Call<MessageResponse> call = apiService.sendNotification(request);
        call.enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d("FCM_TEST", "Notification sent successfully: " + response.body().toString());
                    logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "fcm_sent");
                } else {
                    Log.e("FCM_TEST", "Failed to send: " + response.code() + " - " + response.raw().toString());
                    Log.e("FCM_TEST", "Request body: " + new Gson().toJson(request));
                    logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "fcm_send_failed");
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                Log.e("FCM_TEST", "Error: " + t.getMessage());
                logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "fcm_send_failed");
            }
        });
    }

    private void listenAvailability() {
        db.collection(Constant.KEY_COLLECTION_USERS).document(receiverUser.id).addSnapshotListener(ChatActivity.this, (value, error) -> {
            if (error != null) {
                return;
            }
            if (value != null) {
                if (value.getLong(Constant.KEY_AVAILABILITY) != null) {
                    int availability = Objects.requireNonNull(value.getLong(Constant.KEY_AVAILABILITY)).intValue();
                    isReceiverOnline = availability == 1;
                }
            }
            if (isReceiverOnline) {
                binding.txtAvail.setVisibility(View.VISIBLE);
            } else {
                binding.txtAvail.setVisibility(View.GONE);
            }
        });
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            return;
        }
        if (value != null) {
            int count = chatMessages.size();
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = documentChange.getDocument().getString(Constant.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constant.KEY_RECEIVER_ID);
                    String encryptedMessage = documentChange.getDocument().getString(Constant.KEY_MESSAGE);
                    String decryptedMessage = decryptMessage(encryptedMessage);
                    chatMessage.message = decodeMessage(decryptedMessage);
                    chatMessage.dateTime = getReadDateTime(documentChange.getDocument().getDate(Constant.KEY_TIMESTAMP));
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constant.KEY_TIMESTAMP);
                    chatMessages.add(chatMessage);
                }
            }
            Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
            if (count == 0) {
                chatAdapter.notifyDataSetChanged();
            } else {
                chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatView.smoothScrollToPosition(chatMessages.size() - 1);
            }
            binding.chatView.setVisibility(View.VISIBLE);
        }
        binding.progBar.setVisibility(View.GONE);
        if (conversionId == null) {
            checkForConversion();
        }
    };

    private Bitmap getBitmapFromEncodedString(String encodedImage) {
        if (encodedImage != null) {
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } else {
            return null;
        }
    }

    private void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constant.KEY_USER);
        if (receiverUser == null) {
            Log.e("ChatActivity", "Receiver user is null, finishing activity");
            logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "receiver_user_null");
            finish();
            return;
        }
        binding.txtName.setText(receiverUser.name);

        if (receiverUser.token == null || receiverUser.image == null) {
            fetchReceiverDetailsFromFirestore();
        }
    }

    private void fetchReceiverDetailsFromFirestore() {
        db.collection(Constant.KEY_COLLECTION_USERS).document(receiverUser.id).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                DocumentSnapshot document = task.getResult();
                if (receiverUser.token == null) {
                    receiverUser.token = document.getString(Constant.KEY_FCM_TOKEN);
                }
                if (receiverUser.image == null) {
                    receiverUser.image = document.getString(Constant.KEY_IMAGE);
                }
                chatAdapter = new ChatAdapter(chatMessages, getBitmapFromEncodedString(receiverUser.image), preferenceManager.getString(Constant.KEY_USER_ID));
                binding.chatView.setAdapter(chatAdapter);
            } else {
                Log.e("FCM_TEST", "Failed to fetch receiver details: " + task.getException());
                logChatEvent(preferenceManager.getString(Constant.KEY_USER_ID), "fetch_receiver_failed");
            }
        });
    }

    private void setListener() {
        binding.imgBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v -> sendMessage());
    }

    private String getReadDateTime(Date date) {
        return new SimpleDateFormat("dd MMMM, YYYY - hh:mm a", Locale.getDefault()).format(date);
    }

    private void updateConversion(String message) {
        DocumentReference documentReference = db.collection(Constant.KEY_COLLECTION_CONVERSATIONS).document(conversionId);
        documentReference.update(
                Constant.KEY_LAST_MESSAGE, message,
                Constant.KEY_TIMESTAMP, new Date()
        ).addOnFailureListener(e -> Log.e("ChatActivity", "Failed to update conversion: " + e.getMessage()));
    }

    private void addConversion(HashMap<String, Object> conversion) {
        db.collection(Constant.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId())
                .addOnFailureListener(e -> Log.e("ChatActivity", "Failed to add conversion: " + e.getMessage()));
    }

    private void checkForConversion() {
        if (chatMessages.size() != 0) {
            checkForConversionRemotely(preferenceManager.getString(Constant.KEY_USER_ID), receiverUser.id);
            checkForConversionRemotely(receiverUser.id, preferenceManager.getString(Constant.KEY_USER_ID));
        }
    }

    private void checkForConversionRemotely(String senderId, String receiverId) {
        db.collection(Constant.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constant.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constant.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
                        DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
                        conversionId = documentSnapshot.getId();
                    }
                });
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailability();
    }
}