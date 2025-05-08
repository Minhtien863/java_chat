package com.androids.javachat.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.androids.javachat.adapter.UsersAdapter;
import com.androids.javachat.databinding.ActivityUsersBinding;
import com.androids.javachat.listener.Userlistener;
import com.androids.javachat.models.User;
import com.androids.javachat.utilities.Constant;
import com.androids.javachat.utilities.PreferenceManager;
import com.androids.javachat.utilities.SpaceItemDecoration;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UsersActivity extends BaseActivity implements Userlistener {

    private ActivityUsersBinding binding;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setListeners();
        getUser();
    }

    private void setListeners() {
        binding.imgBack.setOnClickListener(v -> onBackPressed());
    }

    private void getUser() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.e("UsersActivity", "No authenticated user found");
            showErrorMessage("Vui lòng đăng nhập lại");
            return;
        }
        String currentUserId = auth.getCurrentUser().getUid();
        Log.d("UsersActivity", "Current user ID: " + currentUserId);
        Log.d("UsersActivity", "Auth state: Authenticated, UID: " + currentUserId);

        loading(true);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(Constant.KEY_COLLECTION_USERS)
                .whereEqualTo("isEmailVerified", true)
                .limit(50)
                .get()
                .addOnCompleteListener(task -> {
                    loading(false);
                    if (task.isSuccessful() && task.getResult() != null) {
                        Log.d("UsersActivity", "Found " + task.getResult().size() + " profiles");
                        List<User> users = new ArrayList<>();
                        for (QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()) {
                            String documentId = queryDocumentSnapshot.getId();
                            if (documentId.equals(currentUserId)) {
                                Log.d("UsersActivity", "Skipping current user: " + documentId);
                                continue;
                            }
                            String name = queryDocumentSnapshot.getString(Constant.KEY_NAME);
                            if (name == null || name.isEmpty()) {
                                Log.w("UsersActivity", "Missing name for user: " + documentId);
                                continue;
                            }
                            Log.d("UsersActivity", "Profile: ID=" + documentId +
                                    ", Name=" + name +
                                    ", Email=" + queryDocumentSnapshot.getString(Constant.KEY_EMAIL) +
                                    ", isEmailVerified=" + queryDocumentSnapshot.getBoolean("isEmailVerified"));
                            User user = new User();
                            user.id = documentId;
                            user.name = name;
                            user.email = queryDocumentSnapshot.getString(Constant.KEY_EMAIL);
                            user.image = queryDocumentSnapshot.getString(Constant.KEY_IMAGE);
                            user.token = queryDocumentSnapshot.getString(Constant.KEY_FCM_TOKEN);
                            user.isEmailVerified = queryDocumentSnapshot.getBoolean("isEmailVerified");
                            users.add(user);
                        }
                        if (!users.isEmpty()) {
                            Log.d("UsersActivity", "Displaying " + users.size() + " users");
                            int spacingInPixels = (int) (16 * getResources().getDisplayMetrics().density);
                            binding.userRecyclerview.addItemDecoration(new SpaceItemDecoration(spacingInPixels));
                            UsersAdapter usersAdapter = new UsersAdapter(users, this);
                            binding.userRecyclerview.setAdapter(usersAdapter);
                            binding.userRecyclerview.setVisibility(View.VISIBLE);
                            binding.txtErrorMessage.setVisibility(View.GONE);
                        } else {
                            Log.w("UsersActivity", "No verified users found");
                            showErrorMessage("Không tìm thấy người dùng đã xác minh. Hãy mời bạn bè tham gia!");
                        }
                    } else {
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Log.e("UsersActivity", "Query failed: " + errorMsg);
                        showErrorMessage("Lỗi kết nối: " + errorMsg);
                    }
                });
    }

    private void showErrorMessage(String message) {
        binding.txtErrorMessage.setText(message);
        binding.txtErrorMessage.setVisibility(View.VISIBLE);
        binding.userRecyclerview.setVisibility(View.GONE);
    }

    private void loading(Boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onUserClicked(User user) {
        Log.d("UsersActivity", "User clicked: ID=" + user.id + ", Name=" + user.name);
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constant.KEY_USER, user);
        startActivity(intent);
        finish();
    }
}