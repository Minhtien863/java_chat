package com.androids.javachat.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.util.Log;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
        setListener();
        loadReciverDetails();
        init();
        listenMessage();
    }

    private void init() {
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedString(receiverUser.image),
                preferenceManager.getString(Constant.KEY_USER_ID)
        );
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

    private void sendMessage() {
        HashMap<String, Object> message = new HashMap<>();
        String messageText = binding.inputMessage.getText().toString();
        message.put(Constant.KEY_SENDER_ID, preferenceManager.getString(Constant.KEY_USER_ID));
        message.put(Constant.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constant.KEY_MESSAGE, messageText);
        message.put(Constant.KEY_TIMESTAMP, new Date());
        db.collection(Constant.KEY_COLLECTION_CHAT).add(message).addOnSuccessListener(documentReference -> {
            // Kiểm tra và lấy token trước khi gửi thông báo
            if (receiverUser.token == null) {
                fetchReceiverFcmTokenFromFirestoreAndSend(messageText);
            } else {
                sendNotificationToReceiver(messageText);
            }
        }).addOnFailureListener(e -> {
            Log.e("Firestore", "Failed to send message: " + e.getMessage());
        });
        binding.inputMessage.setText(null);
        if (conversionId != null) {
            updateConversion(messageText);
        } else {
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constant.KEY_SENDER_ID, preferenceManager.getString(Constant.KEY_USER_ID));
            conversion.put(Constant.KEY_SENDER_NAME, preferenceManager.getString(Constant.KEY_NAME));
            conversion.put(Constant.KEY_SENDER_IMG, preferenceManager.getString(Constant.KEY_IMAGE));
            conversion.put(Constant.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constant.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constant.KEY_RECEIVER_IMG, receiverUser.image);
            conversion.put(Constant.KEY_LAST_MESSAGE, messageText);
            conversion.put(Constant.KEY_TIMESTAMP, new Date());
            addConversion(conversion);
        }
    }
    private void fetchReceiverFcmTokenFromFirestoreAndSend(String messageText) {
        db.collection(Constant.KEY_COLLECTION_USERS)
                .document(receiverUser.id)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot document = task.getResult();
                        receiverUser.token = document.getString("fcmtoken");
                        if (receiverUser.token != null && !receiverUser.token.isEmpty()) {
                            sendNotificationToReceiver(messageText);
                        } else {
                            Log.e("FCM_TEST", "FCM token still not found for receiver after fetch");
                        }
                    } else {
                        Log.e("FCM_TEST", "Failed to fetch FCM token: " + task.getException());
                    }
                });
    }

    private void sendNotificationToReceiver(String messageText) {
        // Lấy access token từ PreferenceManager để gọi API
        String accessToken = preferenceManager.getFcmToken();
        if (accessToken == null) {
            preferenceManager.refreshFcmToken(new PreferenceManager.OnTokenRefreshListener() {
                @Override
                public void onTokenRefreshed(String newToken) {
                    sendNotificationWithToken(newToken, messageText);
                }

                @Override
                public void onTokenRefreshFailed(Exception e) {
                    Log.e("FCM", "Failed to refresh token: " + e.getMessage());
                }
            });
        } else {
            sendNotificationWithToken(accessToken, messageText);
        }
    }

    private void sendNotificationWithToken(String accessToken, String messageText) {
        if (receiverUser.token == null || receiverUser.token.isEmpty()) {
            Log.e("FCM_TEST", "FCM token not found for receiver");
            return;
        }

        String notificationBody = preferenceManager.getString(Constant.KEY_NAME) + ": " + messageText;

        HashMap<String, String> data = new HashMap<>();
        data.put("title", "Chat Notification");
        data.put("body", notificationBody);
        data.put("senderId", preferenceManager.getString(Constant.KEY_USER_ID));
        data.put("senderName", preferenceManager.getString(Constant.KEY_NAME));

        Log.d("FCM_TEST", "Receiver token: " + receiverUser.token);

        // Tạo JSON thủ công
        HashMap<String, Object> message = new HashMap<>();
        message.put("token", receiverUser.token);
        message.put("data", data);
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("message", message);

        String jsonRequest = new Gson().toJson(requestMap);
        Log.d("FCM_TEST", "Sending request: " + jsonRequest);

        // Tạo MessageRequest từ JSON thủ công
        MessageRequest request = new MessageRequest(new MessageRequest.Message(receiverUser.token, data));

        ApiService apiService = ApiClient.getApiService(accessToken);
        Call<MessageResponse> call = apiService.sendNotification(request);
        call.enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d("FCM_TEST", "Notification sent successfully: " + response.body().toString());
                } else {
                    Log.e("FCM_TEST", "Failed to send: " + response.code() + " - " + response.raw().toString());
                    Log.e("FCM_TEST", "Request body: " + new Gson().toJson(request));
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                Log.e("FCM_TEST", "Error: " + t.getMessage());
            }
        });
    }

    private void listenAvailability() {
        db.collection(Constant.KEY_COLLECTION_USERS)
                .document(receiverUser.id)
                .addSnapshotListener(ChatActivity.this, (value, error) -> {
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
                    chatMessage.message = documentChange.getDocument().getString(Constant.KEY_MESSAGE);
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

    private void loadReciverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constant.KEY_USER);
        binding.txtName.setText(receiverUser.name);

        // Nếu token hoặc image không có, lấy từ Firestore
        if (receiverUser.token == null || receiverUser.image == null) {
            fetchReceiverDetailsFromFirestore();
        } else {
            // Nếu đã có image, dùng trực tiếp
            chatAdapter = new ChatAdapter(
                    chatMessages,
                    getBitmapFromEncodedString(receiverUser.image),
                    preferenceManager.getString(Constant.KEY_USER_ID)
            );
            binding.chatView.setAdapter(chatAdapter);
        }
    }

    private void fetchReceiverDetailsFromFirestore() {
        db.collection(Constant.KEY_COLLECTION_USERS)
                .document(receiverUser.id)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot document = task.getResult();
                        if (receiverUser.token == null) {
                            receiverUser.token = document.getString("fcmtoken");
                        }
                        if (receiverUser.image == null) {
                            receiverUser.image = document.getString(Constant.KEY_IMAGE);
                        }
                        // Cập nhật adapter với ảnh mới
                        chatAdapter = new ChatAdapter(
                                chatMessages,
                                getBitmapFromEncodedString(receiverUser.image),
                                preferenceManager.getString(Constant.KEY_USER_ID)
                        );
                        binding.chatView.setAdapter(chatAdapter);
                    } else {
                        Log.e("FCM_TEST", "Failed to fetch receiver details: " + task.getException());
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
        DocumentReference documentReference = db.collection(Constant.KEY_COLLECTION_CONVERSATIONS)
                .document(conversionId);
        documentReference.update(
                Constant.KEY_LAST_MESSAGE, message,
                Constant.KEY_TIMESTAMP, new Date()
        );
    }

    private void addConversion(HashMap<String, Object> conversion) {
        db.collection(Constant.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }

    private void checkForConversion() {
        if (chatMessages.size() != 0) {
            checkForConversionRemotely(
                    preferenceManager.getString(Constant.KEY_USER_ID),
                    receiverUser.id
            );
            checkForConversionRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constant.KEY_USER_ID)
            );
        }
    }

    private void checkForConversionRemotely(String senderId, String receiverId) {
        db.collection(Constant.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constant.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constant.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task -> {
        if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversionId = documentSnapshot.getId();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailability();
    }
}