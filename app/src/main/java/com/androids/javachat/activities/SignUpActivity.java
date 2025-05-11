package com.androids.javachat.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.androids.javachat.R;
import com.androids.javachat.databinding.ActivitySignUpBinding;
import com.androids.javachat.utilities.Constant;
import com.androids.javachat.utilities.PreferenceManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;

public class SignUpActivity extends AppCompatActivity {

    private ActivitySignUpBinding binding;
    private String encodedImage;
    private PreferenceManager preferenceManager;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Handler verificationHandler;
    private Runnable verificationRunnable;
    private static final long VERIFICATION_TIMEOUT = 5 * 60 * 1000; // 5 minutes
    private boolean isVerifying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        verificationHandler = new Handler(Looper.getMainLooper());
        setListener();
    }

    private void setListener() {
        binding.txtSignUp.setOnClickListener(v -> onBackPressed());
        binding.btnSignUp.setOnClickListener(v -> {
            if (isValidSignUpDetails() && !isVerifying) {
                signUp();
            }
        });

        binding.layoutImage.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pickImage.launch(intent);
        });
    }

    private void signUp() {
        isVerifying = true;
        loading(true);
        String email = binding.inputEmail.getText().toString().trim();
        String password = binding.inputPassword.getText().toString().trim();
        String name = binding.inputName.getText().toString().trim();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (mAuth.getCurrentUser() == null) {
                            stopVerification();
                            showToast("Lỗi xác thực, vui lòng thử lại");
                            logSessionEvent(null, "auth_error");
                            return;
                        }
                        String userId = mAuth.getCurrentUser().getUid();
                        Log.d("SignUpActivity", "User created: ID=" + userId + ", Name=" + name);

                        mAuth.getCurrentUser().sendEmailVerification()
                                .addOnCompleteListener(verifyTask -> {
                                    if (!verifyTask.isSuccessful()) {
                                        stopVerification();
                                        showToast("Không thể gửi email xác minh: " + verifyTask.getException().getMessage());
                                        logSessionEvent(userId, "email_verification_failed");
                                        mAuth.signOut();
                                        return;
                                    }

                                    preferenceManager.putString(Constant.KEY_USER_ID, userId);
                                    preferenceManager.putString(Constant.KEY_NAME, name);
                                    preferenceManager.putString(Constant.KEY_EMAIL, email);
                                    preferenceManager.putString(Constant.KEY_IMAGE, encodedImage);

                                    showToast("Vui lòng xác minh email qua Gmail");
                                    binding.txtSignUp.setText("Hủy");
                                    binding.txtSignUp.setOnClickListener(v -> {
                                        stopVerification();
                                        mAuth.signOut();
                                        showToast("Đã hủy xác minh");
                                        logSessionEvent(userId, "verification_cancelled");
                                    });

                                    startVerificationCheck(userId, name, email);
                                });
                    } else {
                        stopVerification();
                        try {
                            throw task.getException();
                        } catch (FirebaseAuthUserCollisionException e) {
                            showToast("Email đã được sử dụng");
                            logSessionEvent(null, "email_collision");
                        } catch (FirebaseAuthWeakPasswordException e) {
                            showToast("Mật khẩu quá yếu, vui lòng chọn mật khẩu mạnh hơn");
                            logSessionEvent(null, "weak_password");
                        } catch (Exception e) {
                            showToast("Đăng ký thất bại: " + e.getMessage());
                            logSessionEvent(null, "sign_up_failed");
                        }
                    }
                });
    }

    private void startVerificationCheck(String userId, String name, String email) {
        final long startTime = System.currentTimeMillis();
        verificationRunnable = new Runnable() {
            @Override
            public void run() {
                if (mAuth.getCurrentUser() == null) {
                    stopVerification();
                    showToast("Phiên xác thực đã hết hạn, vui lòng thử lại");
                    logSessionEvent(userId, "auth_expired");
                    return;
                }

                mAuth.getCurrentUser().reload().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser().isEmailVerified()) {
                        Log.d("SignUpActivity", "Email verified for user: " + userId);
                        checkSessionAndSaveUserData(userId, name, email);
                    } else if (System.currentTimeMillis() - startTime > VERIFICATION_TIMEOUT) {
                        stopVerification();
                        showToast("Hết thời gian xác minh, vui lòng thử lại");
                        logSessionEvent(userId, "verification_timeout");
                        mAuth.signOut();
                    } else {
                        verificationHandler.postDelayed(this, 2000);
                    }
                });
            }
        };
        verificationHandler.post(verificationRunnable);
    }

    private void checkSessionAndSaveUserData(String userId, String name, String email) {
        db.collection(Constant.KEY_COLLECTION_USERS)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String currentToken = preferenceManager.getDeviceFcmToken();
                    if (documentSnapshot.exists()) {
                        String storedToken = documentSnapshot.getString(Constant.KEY_FCM_TOKEN);
                        if (storedToken != null && !storedToken.equals(currentToken)) {
                            showToast("Tài khoản đang được đăng nhập trên thiết bị khác");
                            logSessionEvent(userId, "session_conflict");
                            mAuth.signOut();
                            preferenceManager.clear();
                            preferenceManager.putBoolean(Constant.KEY_SIGNED_IN, false);
                            stopVerification();
                            return;
                        }
                    }
                    saveUserData(userId, name, email);
                })
                .addOnFailureListener(e -> {
                    showToast("Lỗi kiểm tra phiên: " + e.getMessage());
                    logSessionEvent(userId, "session_check_failed");
                    mAuth.signOut();
                    preferenceManager.clear();
                    preferenceManager.putBoolean(Constant.KEY_SIGNED_IN, false);
                    stopVerification();
                });
    }

    private void saveUserData(String userId, String name, String email) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
            String fcmToken = tokenTask.isSuccessful() ? tokenTask.getResult() : null;
            if (!tokenTask.isSuccessful() || fcmToken == null) {
                Log.e("SignUpActivity", "Failed to get FCM token");
                showToast("Không thể lấy FCM token, dữ liệu sẽ được đồng bộ sau");
            }

            HashMap<String, Object> user = new HashMap<>();
            user.put(Constant.KEY_NAME, name);
            user.put(Constant.KEY_EMAIL, email);
            user.put(Constant.KEY_IMAGE, encodedImage);
            user.put(Constant.KEY_FCM_TOKEN, fcmToken != null ? fcmToken : "");
            user.put(Constant.KEY_AVAILABILITY, 1);
            user.put("isEmailVerified", true);

            db.collection(Constant.KEY_COLLECTION_USERS)
                    .document(userId)
                    .set(user)
                    .addOnSuccessListener(documentReference -> {
                        Log.d("SignUpActivity", "User data saved: ID=" + userId + ", Name=" + name);
                        preferenceManager.putBoolean(Constant.KEY_SIGNED_IN, true);
                        preferenceManager.putString(Constant.KEY_USER_ID, userId);
                        preferenceManager.putString(Constant.KEY_NAME, name);
                        preferenceManager.putString(Constant.KEY_EMAIL, email);
                        preferenceManager.putString(Constant.KEY_IMAGE, encodedImage);
                        if (fcmToken != null) {
                            preferenceManager.saveDeviceFcmToken(fcmToken);
                        }
                        stopVerification();
                        showToast("Đăng ký thành công!");
                        logSessionEvent(userId, "sign_up_success");
                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(exception -> {
                        Log.e("SignUpActivity", "Failed to save user data: " + exception.getMessage());
                        stopVerification();
                        showToast("Lưu thông tin thất bại: " + exception.getMessage());
                        logSessionEvent(userId, "save_user_failed");
                        mAuth.signOut();
                    });
        });
    }

    private void logSessionEvent(String userId, String action) {
        HashMap<String, Object> log = new HashMap<>();
        log.put("userId", userId != null ? userId : "unknown");
        log.put("action", action);
        log.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        db.collection("logs").add(log)
                .addOnFailureListener(e -> Log.e("SignUpActivity", "Failed to log session event: " + e.getMessage()));
    }

    private void stopVerification() {
        isVerifying = false;
        loading(false);
        binding.txtSignUp.setText("Đăng nhập");
        binding.txtSignUp.setOnClickListener(v -> onBackPressed());
        if (verificationRunnable != null) {
            verificationHandler.removeCallbacks(verificationRunnable);
        }
    }

    private String encodeImage(Bitmap bitmap) {
        int preWidth = 150;
        int preHeight = bitmap.getHeight() * preWidth / bitmap.getWidth();
        Bitmap preBitmap = Bitmap.createScaledBitmap(bitmap, preWidth, preHeight, false);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        preBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        if (bytes.length > 1024 * 1024) {
            throw new IllegalStateException("Ảnh quá lớn, vui lòng chọn ảnh nhỏ hơn");
        }
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(imageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        binding.imageProfile.setImageBitmap(bitmap);
                        binding.txtAddImage.setVisibility(View.GONE);
                        encodedImage = encodeImage(bitmap);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        showToast("Không thể tải ảnh, vui lòng thử lại");
                    } catch (IllegalStateException e) {
                        showToast(e.getMessage());
                    }
                }
            }
    );

    private boolean isValidSignUpDetails() {
        if (encodedImage == null) {
            showToast("Vui lòng chọn ảnh đại diện");
            binding.imageProfile.setBackgroundResource(R.drawable.image_error_background);
            return false;
        }
        String name = binding.inputName.getText().toString().trim();
        if (name.isEmpty()) {
            showToast("Vui lòng nhập tên của bạn");
            binding.inputName.setError("Name is required");
            return false;
        }
        if (name.matches(".*[<>\"'].*")) {
            showToast("Tên không được chứa ký tự <>\"'");
            binding.inputName.setError("Invalid characters in name");
            return false;
        }
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
            binding.inputPassword.setError("Password must be 8+ characters with letters and numbers");
            return false;
        }
        String confirmPassword = binding.inputConfirmPassword.getText().toString().trim();
        if (confirmPassword.isEmpty()) {
            showToast("Vui lòng xác nhận mật khẩu");
            binding.inputConfirmPassword.setError("Confirm password is required");
            return false;
        }
        if (!password.equals(confirmPassword)) {
            showToast("Mật khẩu và xác nhận mật khẩu không khớp");
            binding.inputConfirmPassword.setError("Passwords do not match");
            return false;
        }
        Log.d("SignUpActivity", "Input validation passed: email=" + email + ", name=" + name);
        return true;
    }

    private void loading(boolean isLoading) {
        if (isLoading) {
            binding.btnSignUp.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
            binding.btnSignUp.setVisibility(View.VISIBLE);
        }
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }
}