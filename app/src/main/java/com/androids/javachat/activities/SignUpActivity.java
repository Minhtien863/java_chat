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
    private static final long VERIFICATION_TIMEOUT = 5 * 60 * 1000; // 5 phút
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
                            return;
                        }
                        String userId = mAuth.getCurrentUser().getUid();
                        Log.d("SignUpActivity", "User created: ID=" + userId + ", Name=" + name);

                        mAuth.getCurrentUser().sendEmailVerification()
                                .addOnCompleteListener(verifyTask -> {
                                    if (!verifyTask.isSuccessful()) {
                                        stopVerification();
                                        showToast("Không thể gửi email xác minh: " + verifyTask.getException().getMessage());
                                        mAuth.signOut();
                                        return;
                                    }

                                    // Lưu tạm thời vào PreferenceManager
                                    preferenceManager.putString(Constant.KEY_USER_ID, userId);
                                    preferenceManager.putString(Constant.KEY_NAME, name);
                                    preferenceManager.putString(Constant.KEY_EMAIL, email);
                                    preferenceManager.putString(Constant.KEY_IMAGE, encodedImage);

                                    // Hiển thị thông báo chờ xác minh
                                    showToast("Vui lòng xác minh email qua Gmail");
                                    binding.txtSignUp.setText("Hủy");
                                    binding.txtSignUp.setOnClickListener(v -> {
                                        stopVerification();
                                        mAuth.signOut();
                                        showToast("Đã hủy xác minh");
                                    });

                                    // Bắt đầu kiểm tra xác minh định kỳ
                                    startVerificationCheck(userId, name, email);
                                });
                    } else {
                        stopVerification();
                        try {
                            throw task.getException();
                        } catch (FirebaseAuthUserCollisionException e) {
                            showToast("Email đã được sử dụng");
                        } catch (FirebaseAuthWeakPasswordException e) {
                            showToast("Mật khẩu quá yếu, vui lòng chọn mật khẩu mạnh hơn");
                        } catch (Exception e) {
                            showToast("Đăng ký thất bại: " + e.getMessage());
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
                    return;
                }

                mAuth.getCurrentUser().reload().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser().isEmailVerified()) {
                        Log.d("SignUpActivity", "Email verified for user: " + userId);
                        saveUserData(userId, name, email);
                    } else if (System.currentTimeMillis() - startTime > VERIFICATION_TIMEOUT) {
                        stopVerification();
                        showToast("Hết thời gian xác minh, vui lòng thử lại");
                        mAuth.signOut();
                    } else {
                        // Tiếp tục kiểm tra sau 2 giây
                        verificationHandler.postDelayed(this, 2000);
                    }
                });
            }
        };
        verificationHandler.post(verificationRunnable);
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
                            preferenceManager.putString(Constant.KEY_FCM_TOKEN, fcmToken);
                        }
                        stopVerification();
                        showToast("Đăng ký thành công!");
                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(exception -> {
                        Log.e("SignUpActivity", "Failed to save user data: " + exception.getMessage());
                        stopVerification();
                        showToast("Lưu thông tin thất bại: " + exception.getMessage());
                        mAuth.signOut();
                    });
        });
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
            return false;
        } else if (binding.inputName.getText().toString().trim().isEmpty()) {
            showToast("Vui lòng nhập tên của bạn");
            return false;
        } else if (binding.inputEmail.getText().toString().trim().isEmpty()) {
            showToast("Vui lòng nhập Email");
            return false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(binding.inputEmail.getText().toString()).matches()) {
            showToast("Email không hợp lệ");
            return false;
        } else if (binding.inputPassword.getText().toString().trim().isEmpty()) {
            showToast("Vui lòng nhập mật khẩu");
            return false;
        } else if (binding.inputPassword.getText().toString().trim().length() < 6) {
            showToast("Mật khẩu phải có ít nhất 6 ký tự");
            return false;
        } else if (binding.inputConfirmPassword.getText().toString().trim().isEmpty()) {
            showToast("Vui lòng xác nhận mật khẩu");
            return false;
        } else if (!binding.inputPassword.getText().toString().equals(binding.inputConfirmPassword.getText().toString())) {
            showToast("Mật khẩu và xác nhận mật khẩu không khớp");
            return false;
        }
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