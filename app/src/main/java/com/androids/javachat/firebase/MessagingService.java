package com.androids.javachat.firebase;

import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.androids.javachat.R;
import com.androids.javachat.activities.ChatActivity;
import com.androids.javachat.models.User;
import com.androids.javachat.utilities.Constant;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "chat_notifications";
    private static final String CHANNEL_NAME = "Chat Notifications";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d("FCM", "Token: " + token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d("FCM", "onMessageReceived called");
        Log.d("FCM", "Data: " + remoteMessage.getData().toString());

        if (isAppInForeground()) {
            Log.d("FCM", "App is in foreground, skipping notification");
            return;
        }

        String title = remoteMessage.getData().get("title");
        String body = remoteMessage.getData().get("body");
        String senderId = remoteMessage.getData().get("senderId");
        String senderName = remoteMessage.getData().get("senderName");

        if (title == null || body == null || senderId == null) {
            Log.e("FCM", "Missing required fields in data payload: title=" + title + ", body=" + body + ", senderId=" + senderId);
            return;
        }

        Log.d("FCM", "Message: " + body + ", SenderId: " + senderId);

        // Tạo User object (không có senderImage)
        User sender = new User();
        sender.id = senderId;
        sender.name = senderName;

        // Không cần lấy senderImage từ data payload, sẽ lấy từ Firestore trong ChatActivity
        showNotification(title, body, sender);
    }

    private void showNotification(String title, String message, User sender) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(Constant.KEY_USER, sender);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private boolean isAppInForeground() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            for (ActivityManager.RunningAppProcessInfo processInfo : activityManager.getRunningAppProcesses()) {
                if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        && processInfo.processName.equals(getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }
}