package com.androids.javachat.activities;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.androids.javachat.R;
import com.androids.javachat.databinding.ActivityChatBinding;
import com.androids.javachat.models.User;
import com.androids.javachat.utilities.Constant;
import com.androids.javachat.utilities.PreferenceManager;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private User receiverUser;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListener();
        loadReciverDetails();
    }

    private void loadReciverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constant.KEY_USER);
        binding.txtName.setText(receiverUser.name);
    }

    private void setListener() {
        binding.imgBack.setOnClickListener(v -> onBackPressed());
//        binding.layoutSend.setOnClickListener(v -> sendMessage());
    }
}