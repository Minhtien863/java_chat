package com.androids.javachat.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.androids.javachat.databinding.ItemContainerRecentConversionBinding;
import com.androids.javachat.listener.ConversionListener;
import com.androids.javachat.models.ChatMessage;
import com.androids.javachat.models.User;
import com.androids.javachat.utilities.PreferenceManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class RecentConversationsAdapter extends RecyclerView.Adapter<RecentConversationsAdapter.ConversionViewHolder> {

    private final List<ChatMessage> chatMessagesList;
    private final ConversionListener conversionListener;
    private PreferenceManager preferenceManager;

    public RecentConversationsAdapter(List<ChatMessage> chatMessagesList, ConversionListener conversionListener) {
        this.chatMessagesList = chatMessagesList;
        this.conversionListener = conversionListener;
    }

    @NonNull
    @Override
    public ConversionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (preferenceManager == null) {
            preferenceManager = new PreferenceManager(parent.getContext());
        }
        return new ConversionViewHolder(
                ItemContainerRecentConversionBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                )
        );
    }

    @Override
    public void onBindViewHolder(@NonNull ConversionViewHolder holder, int position) {
        holder.setData(chatMessagesList.get(position));
    }

    @Override
    public int getItemCount() {
        return chatMessagesList.size();
    }

    class ConversionViewHolder extends RecyclerView.ViewHolder {
        ItemContainerRecentConversionBinding binding;

        ConversionViewHolder(ItemContainerRecentConversionBinding itemContainerRecentConversionBinding) {
            super(itemContainerRecentConversionBinding.getRoot());
            binding = itemContainerRecentConversionBinding;
        }

        void setData(ChatMessage chatMessage) {
            binding.imgProfile.setImageBitmap(getConversionImage(chatMessage.conversionImg));
            binding.txtName.setText(chatMessage.conversionName);
            String encryptedMessage = chatMessage.message;
            String decryptedMessage = decryptMessage(encryptedMessage);
            binding.txtRecentMessage.setText(decodeMessage(decryptedMessage));
            binding.getRoot().setOnClickListener(v -> {
                User user = new User();
                user.id = chatMessage.conversionId;
                user.name = chatMessage.conversionName;
                user.image = chatMessage.conversionImg;
                conversionListener.onConversionClicked(user);
            });
        }

        private String decryptMessage(String encryptedMessage) {
            if (encryptedMessage == null || encryptedMessage.isEmpty()) return "";
            try {
                String aesKey = preferenceManager.getString("AES_KEY");
                if (aesKey == null) {
                    Log.e("RecentConversationsAdapter", "AES key not found for decryption");
                    return encryptedMessage;
                }
                byte[] keyBytes = Base64.decode(aesKey, Base64.DEFAULT);
                byte[] encryptedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT);
                byte[] iv = new byte[16];
                byte[] ciphertext = new byte[encryptedBytes.length - 16];
                System.arraycopy(encryptedBytes, 0, iv, 0, 16);
                System.arraycopy(encryptedBytes, 16, ciphertext, 0, encryptedBytes.length - 16);
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(iv));
                byte[] decryptedBytes = cipher.doFinal(ciphertext);
                return new String(decryptedBytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                Log.e("RecentConversationsAdapter", "Decryption failed: " + e.getMessage());
                return encryptedMessage;
            }
        }

        private String decodeMessage(String message) {
            if (message == null) return "";
            return message
                    .replace("\\u003C", "<")
                    .replace("\\u003E", ">")
                    .replace("\\u0022", "\"")
                    .replace("\\u0027", "'");
        }
    }

    private Bitmap getConversionImage(String encodedImg) {
        byte[] bytes = Base64.decode(encodedImg, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}