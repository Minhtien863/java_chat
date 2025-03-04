package com.androids.javachat.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.androids.javachat.databinding.ActivitySignUpBinding;
import com.androids.javachat.utilities.Constant;
import com.androids.javachat.utilities.PasswordUtils;
import com.androids.javachat.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;

public class SignUpActivity extends AppCompatActivity {

    private ActivitySignUpBinding binding;
    private String encodedImage;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        db = FirebaseFirestore.getInstance();
        setListener();
    }

    private void setListener() {
        binding.txtSignUp.setOnClickListener(v -> onBackPressed());
        binding.btnSignUp.setOnClickListener(v -> {
            if (isValidSignUpDetails()) {
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
        loading(true);

        // Kiểm tra email trùng lặp
        checkEmailExists(binding.inputEmail.getText().toString().trim(), () -> {
            // Lấy FCM token
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                    loading(false);
                    showToast("Không thể lấy FCM token, vui lòng thử lại");
                    return;
                }

                String fcmToken = task.getResult();
                String password = binding.inputPassword.getText().toString().trim();
                String salt = PasswordUtils.generateSalt(); // Tạo muối
                String hashedPassword = PasswordUtils.hashPassword(password, salt); // Mã hóa mật khẩu

                HashMap<String, Object> user = new HashMap<>();
                user.put(Constant.KEY_NAME, binding.inputName.getText().toString().trim());
                user.put(Constant.KEY_EMAIL, binding.inputEmail.getText().toString().trim());
                user.put(Constant.KEY_PASSWORD, hashedPassword); // Lưu mật khẩu đã mã hóa
                user.put(Constant.KEY_SALT, salt); // Lưu muối
                user.put(Constant.KEY_IMAGE, encodedImage);
                user.put(Constant.KEY_FCM_TOKEN, fcmToken);

                // Lưu user vào Firestore
                db.collection(Constant.KEY_COLLECTION_USERS)
                        .add(user)
                        .addOnSuccessListener(documentReference -> {
                            preferenceManager.putBoolean(Constant.KEY_SIGNED_IN, true);
                            preferenceManager.putString(Constant.KEY_USER_ID, documentReference.getId());
                            preferenceManager.putString(Constant.KEY_NAME, binding.inputName.getText().toString().trim());
                            preferenceManager.putString(Constant.KEY_IMAGE, encodedImage);
                            preferenceManager.putString(Constant.KEY_FCM_TOKEN, fcmToken);
                            loading(false);
                            showToast("Đăng ký thành công!");
                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        })
                        .addOnFailureListener(exception -> {
                            loading(false);
                            showToast("Đăng ký thất bại: " + exception.getMessage());
                        });
            });
        });
    }

    private void checkEmailExists(String email, Runnable onSuccess) {
        db.collection(Constant.KEY_COLLECTION_USERS)
                .whereEqualTo(Constant.KEY_EMAIL, email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            loading(false);
                            showToast("Email đã được sử dụng, vui lòng chọn email khác");
                        } else {
                            onSuccess.run();
                        }
                    } else {
                        loading(false);
                        showToast("Lỗi kiểm tra email: " + task.getException().getMessage());
                    }
                });
    }

    private String encodeImage(Bitmap bitmap) {
        int preWidth = 150;
        int preHeight = bitmap.getHeight() * preWidth / bitmap.getWidth();
        Bitmap preBitmap = Bitmap.createScaledBitmap(bitmap, preWidth, preHeight, false);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        preBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
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