package com.androids.javachat.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.androids.javachat.utilities.Constant;
import com.androids.javachat.utilities.PreferenceManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class BaseActivity extends AppCompatActivity {

    private DocumentReference documentReference;
    protected PreferenceManager preferenceManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(getApplicationContext(), SignInActivity.class));
            finish();
            return;
        }
        preferenceManager = new PreferenceManager(getApplicationContext());
        documentReference = FirebaseFirestore.getInstance()
                .collection(Constant.KEY_COLLECTION_USERS)
                .document(preferenceManager.getString(Constant.KEY_USER_ID));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (documentReference != null) {
            documentReference.update(Constant.KEY_AVAILABILITY, 0)
                    .addOnFailureListener(e -> {
                        // Có thể log lỗi nếu cần
                    });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (documentReference != null) {
            documentReference.update(Constant.KEY_AVAILABILITY, 1)
                    .addOnFailureListener(e -> {
                        // Có thể log lỗi nếu cần
                    });
        }
    }
}