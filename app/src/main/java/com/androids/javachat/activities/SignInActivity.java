package com.androids.javachat.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.androids.javachat.databinding.ActivitySignInBinding;
import com.androids.javachat.utilities.Constant;
import com.androids.javachat.utilities.PasswordUtils;
import com.androids.javachat.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public class SignInActivity extends AppCompatActivity {

    private ActivitySignInBinding binding;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore db;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        db = FirebaseFirestore.getInstance();
        if (preferenceManager.getBoolean(Constant.KEY_SIGNED_IN)) {
            startActivity(new Intent(getApplicationContext(), MainActivity.class));
            finish();
        }
        setListeners();
    }

    private void setListeners() {
        binding.txtCreateNewAccount.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), SignUpActivity.class)));
        binding.btnSignIn.setOnClickListener(v -> {
            if (!isProcessing && isValidSignInDetails()) {
                signIn();
            }
        });
    }

    private void signIn() {
        isProcessing = true;
        loading(true);
        String email = binding.inputEmail.getText().toString().trim();
        String password = binding.inputPassword.getText().toString().trim();

        // Truy vấn user bằng email để lấy muối và mật khẩu đã mã hóa
        db.collection(Constant.KEY_COLLECTION_USERS)
                .whereEqualTo(Constant.KEY_EMAIL, email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().getDocuments().isEmpty()) {
                        DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
                        String storedHashedPassword = documentSnapshot.getString(Constant.KEY_PASSWORD);
                        String salt = documentSnapshot.getString(Constant.KEY_SALT);

                        if (salt == null || storedHashedPassword == null) {
                            loading(false);
                            isProcessing = false;
                            showToast("Dữ liệu tài khoản không hợp lệ, vui lòng liên hệ hỗ trợ");
                            return;
                        }

                        // Mã hóa mật khẩu nhập vào với muối từ Firestore
                        String inputHashedPassword = PasswordUtils.hashPassword(password, salt);

                        // So sánh mật khẩu mã hóa
                        if (inputHashedPassword.equals(storedHashedPassword)) {
                            // Đăng nhập thành công
                            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
                                if (tokenTask.isSuccessful() && tokenTask.getResult() != null) {
                                    String fcmToken = tokenTask.getResult();
                                    preferenceManager.putString(Constant.KEY_FCM_TOKEN, fcmToken);
                                    db.collection(Constant.KEY_COLLECTION_USERS)
                                            .document(documentSnapshot.getId())
                                            .update(Constant.KEY_FCM_TOKEN, fcmToken)
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(this, "Cảnh báo: Không thể cập nhật FCM token", Toast.LENGTH_SHORT).show();
                                            });
                                } else {
                                    Toast.makeText(this, "Cảnh báo: Không thể lấy FCM token", Toast.LENGTH_SHORT).show();
                                }

                                preferenceManager.putBoolean(Constant.KEY_SIGNED_IN, true);
                                preferenceManager.putString(Constant.KEY_USER_ID, documentSnapshot.getId());
                                preferenceManager.putString(Constant.KEY_NAME, documentSnapshot.getString(Constant.KEY_NAME));
                                preferenceManager.putString(Constant.KEY_IMAGE, documentSnapshot.getString(Constant.KEY_IMAGE));
                                loading(false);
                                isProcessing = false;
                                showToast("Đăng nhập thành công!");
                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            loading(false);
                            isProcessing = false;
                            showToast("Sai mật khẩu");
                        }
                    } else if (task.isSuccessful()) {
                        loading(false);
                        isProcessing = false;
                        showToast("Email không tồn tại");
                    } else {
                        loading(false);
                        isProcessing = false;
                        showToast("Đăng nhập thất bại: " + task.getException().getMessage());
                    }
                });
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
        if (binding.inputEmail.getText().toString().trim().isEmpty()) {
            showToast("Vui lòng nhập Email");
            return false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(binding.inputEmail.getText().toString()).matches()) {
            showToast("Email không hợp lệ");
            return false;
        } else if (binding.inputPassword.getText().toString().trim().isEmpty()) {
            showToast("Vui lòng nhập mật khẩu");
            return false;
        }
        return true;
    }
}