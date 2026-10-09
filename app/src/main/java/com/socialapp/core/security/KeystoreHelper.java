package com.socialapp.core.security;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class KeystoreHelper {

    private static final String TAG = "KeystoreHelper";
    private static final String KEY_ALIAS = "SocialAppSecurityKey";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;

    public KeystoreHelper(Context context) {
        initKeyStore();
    }

    private void initKeyStore() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build();
                keyGenerator.init(keyGenParameterSpec);
                keyGenerator.generateKey();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize KeyStore", e);
        }
    }

    private SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
        if (entry != null) {
            return entry.getSecretKey();
        }
        return null;
    }

    @Nullable
    public String encrypt(@Nullable String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        try {
            SecretKey secretKey = getSecretKey();
            if (secretKey == null) return plainText;

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.encodeToString(combined, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Encryption error", e);
            return plainText;
        }
    }

    @Nullable
    public String decrypt(@Nullable String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return cipherText;
        try {
            SecretKey secretKey = getSecretKey();
            if (secretKey == null) return cipherText;

            byte[] combined = Base64.decode(cipherText, Base64.DEFAULT);
            if (combined.length <= 12) return cipherText; // GCM IV length is 12 bytes

            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "Decryption error", e);
            return cipherText;
        }
    }
}
