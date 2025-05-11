package com.androids.javachat.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.androids.javachat.databinding.ActivitySignInBinding;
import com.androids.javachat.utilities.Constant;
import com.androids.javachat.utilities.PreferenceManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.UUID;

public class SignInActivity extends AppCompatActivity {

    private ActivitySignInBinding binding;
    private PreferenceManager preferenceManager;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        if (mAuth.getCurrentUser() != null && preferenceManager.getBoolean(Constant.KEY_SIGNED_IN)) {
            checkSessionAndProceed();
        }
        setListeners();
    }

    private void checkSessionAndProceed() {
        String userId = mAuth.getCurrentUser().getUid();
        String storedSessionToken = preferenceManager.getString(Constant.KEY_SESSION_TOKEN); // Lấy session token cục bộ
        db.collection(Constant.KEY_COLLECTION_USERS)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String firestoreSessionToken = documentSnapshot.getString(Constant.KEY_SESSION_TOKEN);
                        if (firestoreSessionToken != null && storedSessionToken != null && !firestoreSessionToken.equals(storedSessionToken)) {
                            showToast("Tài khoản đang được đăng nhập trên thiết bị khác");
                            logSessionEvent(userId, "session_conflict");
                            mAuth.signOut();
                            preferenceManager.clear();
                            preferenceManager.putBoolean(Constant.KEY_SIGNED_IN, false);
                        } else {
                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }
                    } else {
                        showToast("Tài khoản không tồn tại trong Firestore");
                        logSessionEvent(userId, "account_not_found");
                        mAuth.signOut();
                        preferenceManager.clear();
                        preferenceManager.putBoolean(Constant.KEY_SIGNED_IN, false);
                    }
                })
                .addOnFailureListener(e -> {
                    showToast("Lỗi kiểm tra phiên: " + e.getMessage());
                    logSessionEvent(userId, "session_check_failed");
                    mAuth.signOut();
                    preferenceManager.clear();
                    preferenceManager.putBoolean(Constant.KEY_SIGNED_IN, false);
                });
    }

    private void setListeners() {
        binding.txtCreateNewAccount.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), SignUpActivity.class)));
        binding.btnSignIn.setOnClickListener(v -> {
            if (!isProcessing && isValidSignInDetails()) {
                if (checkLoginAttempts()) {
                    signIn();
                } else {
                    showToast("Đã vượt quá số lần thử đăng nhập, vui lòng thử lại sau 5 phút");
                }
            }
        });
    }

    private boolean checkLoginAttempts() {
        int attempts = preferenceManager.getString(Constant.KEY_LOGIN_ATTEMPTS) != null ?
                Integer.parseInt(preferenceManager.getString(Constant.KEY_LOGIN_ATTEMPTS)) : 0;
        long lastAttemptTime = preferenceManager.getString(Constant.KEY_ATTEMPT_TIMESTAMP) != null ?
                Long.parseLong(preferenceManager.getString(Constant.KEY_ATTEMPT_TIMESTAMP)) : 0;
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAttemptTime > Constant.ATTEMPT_WINDOW) {
            preferenceManager.putString(Constant.KEY_LOGIN_ATTEMPTS, "0");
            preferenceManager.putString(Constant.KEY_ATTEMPT_TIMESTAMP, String.valueOf(currentTime));
            return true;
        }

        if (attempts >= Constant.MAX_LOGIN_ATTEMPTS) {
            return false;
        }

        return true;
    }

    private void incrementLoginAttempts() {
        int attempts = preferenceManager.getString(Constant.KEY_LOGIN_ATTEMPTS) != null ?
                Integer.parseInt(preferenceManager.getString(Constant.KEY_LOGIN_ATTEMPTS)) : 0;
        preferenceManager.putString(Constant.KEY_LOGIN_ATTEMPTS, String.valueOf(attempts + 1));
        preferenceManager.putString(Constant.KEY_ATTEMPT_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
    }

    private void signIn() {
        isProcessing = true;
        loading(true);
        String email = binding.inputEmail.getText().toString().trim();
        String password = binding.inputPassword.getText().toString().trim();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        mAuth.getCurrentUser().reload().addOnCompleteListener(reloadTask -> {
                            if (!mAuth.getCurrentUser().isEmailVerified()) {
                                Log.e("SignInActivity", "Email not verified for user: " + mAuth.getCurrentUser().getUid());
                                loading(false);
                                isProcessing = false;
                                showToast("Vui lòng xác minh email trước khi đăng nhập");
                                logSessionEvent(mAuth.getCurrentUser().getUid(), "email_not_verified");
                                mAuth.signOut();
                                return;
                            }
                            Log.d("SignInActivity", "Email verified for user: " + mAuth.getCurrentUser().getUid());

                            String userId = mAuth.getCurrentUser().getUid();
                            db.collection(Constant.KEY_COLLECTION_USERS)
                                    .document(userId)
                                    .get()
                                    .addOnCompleteListener(userTask -> {
                                        if (!userTask.isSuccessful() || !userTask.getResult().exists()) {
                                            loading(false);
                                            isProcessing = false;
                                            showToast("Tài khoản không tồn tại hoặc lỗi kết nối");
                                            logSessionEvent(userId, "account_not_found");
                                            mAuth.signOut();
                                            return;
                                        }
                                        DocumentSnapshot document = userTask.getResult();

                                        // Tạo session token mới
                                        String sessionToken = UUID.randomUUID().toString();
                                        preferenceManager.putString(Constant.KEY_SESSION_TOKEN, sessionToken);

                                        // Cập nhật Firestore
                                        HashMap<String, Object> updates = new HashMap<>();
                                        updates.put("isEmailVerified", true);
                                        updates.put(Constant.KEY_AVAILABILITY, 1);
                                        updates.put(Constant.KEY_SESSION_TOKEN, sessionToken);

                                        // Lấy FCM token
                                        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
                                            String fcmToken = tokenTask.isSuccessful() ? tokenTask.getResult() : "";
                                            preferenceManager.saveDeviceFcmToken(fcmToken);
                                            updates.put(Constant.KEY_FCM_TOKEN, fcmToken); // Luôn cập nhật, kể cả khi rỗng

                                            db.collection(Constant.KEY_COLLECTION_USERS)
                                                    .document(userId)
                                                    .update(updates)
                                                    .addOnSuccessListener(aVoid -> {
                                                        Log.d("SignInActivity", "Updated user data: sessionToken=" + sessionToken + ", fcmToken=" + fcmToken);
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Log.e("SignInActivity", "Failed to update user data: " + e.getMessage());
                                                        showToast("Cảnh báo: Không thể cập nhật dữ liệu người dùng");
                                                    });

                                            preferenceManager.putBoolean(Constant.KEY_SIGNED_IN, true);
                                            preferenceManager.putString(Constant.KEY_USER_ID, userId);
                                            preferenceManager.putString(Constant.KEY_NAME, document.getString(Constant.KEY_NAME));
                                            preferenceManager.putString(Constant.KEY_EMAIL, document.getString(Constant.KEY_EMAIL));
                                            preferenceManager.putString(Constant.KEY_IMAGE, document.getString(Constant.KEY_IMAGE));
                                            preferenceManager.putString(Constant.KEY_LOGIN_ATTEMPTS, "0");
                                            loading(false);
                                            isProcessing = false;
                                            showToast("Đăng nhập thành công!");
                                            logSessionEvent(userId, "sign_in_success");
                                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            startActivity(intent);
                                            finish();
                                        });
                                    });
                        });
                    } else {
                        loading(false);
                        isProcessing = false;
                        incrementLoginAttempts();
                        try {
                            throw task.getException();
                        } catch (FirebaseAuthInvalidUserException e) {
                            showToast("Email không tồn tại");
                            logSessionEvent(null, "invalid_email");
                        } catch (FirebaseAuthInvalidCredentialsException e) {
                            showToast("Sai mật khẩu");
                            logSessionEvent(null, "invalid_password");
                        } catch (Exception e) {
                            showToast("Đăng nhập thất bại: " + e.getMessage());
                            logSessionEvent(null, "sign_in_failed");
                        }
                    }
                });
    }

    private void logSessionEvent(String userId, String action) {
        HashMap<String, Object> log = new HashMap<>();
        log.put("userId", userId != null ? userId : "unknown");
        log.put("action", action);
        log.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        db.collection("logs").add(log)
                .addOnFailureListener(e -> Log.e("SignInActivity", "Failed to log session event: " + e.getMessage()));
    }

    private void loading(boolean isLoading) {
        if (isLoading) {
            binding.btnSignIn.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
            binding.btnSignIn.setVisibility(View.VISIBLE);
        }
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private boolean isValidSignInDetails() {
        String email = binding.inputEmail.getText().toString().trim();
        if (email.isEmpty()) {
            showToast("Vui lòng nhập Email");
            binding.inputEmail.setError("Email is required");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast("Email không hợp lệ");
            binding.inputEmail.setError("Invalid email format");
            return false;
        }
        String password = binding.inputPassword.getText().toString().trim();
        if (password.isEmpty()) {
            showToast("Vui lòng nhập mật khẩu");
            binding.inputPassword.setError("Password is required");
            return false;
        }
        if (password.length() < 8 || !password.matches(".*[a-zA-Z].*") || !password.matches(".*\\d.*")) {
            showToast("Mật khẩu phải có ít nhất 8 ký tự, bao gồm chữ và số");
            binding.inputPassword.setError("Invalid password format");
            return false;
        }
        Log.d("SignInActivity", "Input validation passed: email=" + email);
        return true;
    }
}