package com.androids.javachat.utilities;

public class Constant {
    //Firebase collection
    public static final String KEY_COLLECTION_USERS = "users";
    public static final String KEY_NAME = "name";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_PREFERENCE_NAME = "chatAppPreference";
    public static final String KEY_SIGNED_IN = "isSignedIn";
    public static final String KEY_USER_ID = "userID";
    public static final String KEY_IMAGE = "image";

    //FCM
    public static final String KEY_FCM_TOKEN = "fcmtoken";
    public static final String DEVICE_FCM_TOKEN = "device_fcm_token";
    public static final String KEY_USER = "user";

    //Chat message
    public static final String KEY_COLLECTION_CHAT = "chat";
    public static final String KEY_SENDER_ID = "senderId";
    public static final String KEY_RECEIVER_ID = "receiverId";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_TIMESTAMP = "timestamp";

    //Conversation
    public static final String KEY_COLLECTION_CONVERSATIONS = "conversations";
    public static final String KEY_SENDER_NAME = "senderName";
    public static final String KEY_RECEIVER_NAME = "receiverName";
    public static final String KEY_SENDER_IMG = "senderImg";
    public static final String KEY_RECEIVER_IMG = "receiverImg";
    public static final String KEY_LAST_MESSAGE = "lastMessage";
    public static final String KEY_AVAILABILITY = "availability";

    //Rate limiting
    public static final String KEY_MESSAGE_COUNT = "message_count";
    public static final String KEY_MESSAGE_TIMESTAMP = "message_timestamp";
    public static final int MAX_MESSAGES_PER_MINUTE = 10;
    public static final long RATE_LIMIT_WINDOW = 60 * 1000; // 1 minute

    //Session management
    public static final String KEY_LOGIN_ATTEMPTS = "login_attempts";
    public static final String KEY_ATTEMPT_TIMESTAMP = "attempt_timestamp";
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final long ATTEMPT_WINDOW = 5 * 60 * 1000; // 5 minutes
    public static final String KEY_SESSION_TOKEN = "sessionToken";
}
