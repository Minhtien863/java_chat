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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

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

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        mAuth.getCurrentUser().reload().addOnCompleteListener(reloadTask -> {
                            if (!mAuth.getCurrentUser().isEmailVerified()) {
                                Log.e("SignInActivity", "Email not verified for user: " + mAuth.getCurrentUser().getUid());
                                loading(false);
                                isProcessing = false;
                                showToast("Vui lòng xác minh email trước khi đăng nhập");
                                mAuth.signOut();
                                return;
                            }
                            Log.d("SignInActivity", "Email verified for user: " + mAuth.getCurrentUser().getUid());

                            String userId = mAuth.getCurrentUser().getUid();
                            db.collection(Constant.KEY_COLLECTION_USERS)
                                    .document(userId)
                                    .get()
                                    .addOnCompleteListener(userTask -> {
                                        if (!userTask.isSuccessful()) {
                                            loading(false);
                                            isProcessing = false;
                                            showToast("Lỗi kết nối, vui lòng kiểm tra mạng");
                                            mAuth.signOut();
                                            return;
                                        }
                                        if (userTask.getResult().exists()) {
                                            db.collection(Constant.KEY_COLLECTION_USERS)
                                                    .document(userId)
                                                    .update("isEmailVerified", true)
                                                    .addOnSuccessListener(aVoid -> {
                                                        Log.d("SignInActivity", "Updated isEmailVerified to true for user: " + userId);
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Log.e("SignInActivity", "Failed to update isEmailVerified: " + e.getMessage());
                                                    });

                                            db.collection(Constant.KEY_COLLECTION_USERS)
                                                    .document(userId)
                                                    .update(Constant.KEY_AVAILABILITY, 1)
                                                    .addOnFailureListener(e -> {
                                                        Log.e("SignInActivity", "Failed to update availability: " + e.getMessage());
                                                    });

                                            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
                                                String fcmToken = tokenTask.isSuccessful() ? tokenTask.getResult() : null;
                                                if (fcmToken != null) {
                                                    preferenceManager.putString(Constant.KEY_FCM_TOKEN, fcmToken);
                                                    db.collection(Constant.KEY_COLLECTION_USERS)
                                                            .document(userId)
                                                            .update(Constant.KEY_FCM_TOKEN, fcmToken)
                                                            .addOnFailureListener(e -> {
                                                                Toast.makeText(this, "Cảnh báo: Không thể cập nhật FCM token", Toast.LENGTH_SHORT).show();
                                                            });
                                                } else {
                                                    Toast.makeText(this, "Cảnh báo: Không thể lấy FCM token", Toast.LENGTH_SHORT).show();
                                                }

                                                preferenceManager.putBoolean(Constant.KEY_SIGNED_IN, true);
                                                preferenceManager.putString(Constant.KEY_USER_ID, userId);
                                                preferenceManager.putString(Constant.KEY_NAME, userTask.getResult().getString(Constant.KEY_NAME));
                                                preferenceManager.putString(Constant.KEY_EMAIL, userTask.getResult().getString(Constant.KEY_EMAIL));
                                                preferenceManager.putString(Constant.KEY_IMAGE, userTask.getResult().getString(Constant.KEY_IMAGE));
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
                                            showToast("Tài khoản không tồn tại trong Firestore");
                                            mAuth.signOut();
                                        }
                                    });
                        });
                    } else {
                        loading(false);
                        isProcessing = false;
                        try {
                            throw task.getException();
                        } catch (FirebaseAuthInvalidUserException e) {
                            showToast("Email không tồn tại");
                        } catch (FirebaseAuthInvalidCredentialsException e) {
                            showToast("Sai mật khẩu");
                        } catch (Exception e) {
                            showToast("Đăng nhập thất bại: " + e.getMessage());
                        }
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