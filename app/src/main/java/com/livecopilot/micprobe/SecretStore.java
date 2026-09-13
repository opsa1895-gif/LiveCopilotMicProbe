package com.livecopilot.micprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecretStore {
    private static final String PREFS = "live_copilot_ai";
    private static final String LEGACY_KEY = "openai_api_key";
    private static final String CIPHER_KEY = "openai_api_key_cipher";
    private static final String IV_KEY = "openai_api_key_iv";
    private static final String KEY_ALIAS = "live_copilot_openai_key_v1";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private SecretStore() {}

    static String loadApiKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cipherText = prefs.getString(CIPHER_KEY, "");
        String iv = prefs.getString(IV_KEY, "");

        if (cipherText != null && !cipherText.isEmpty() && iv != null && !iv.isEmpty()) {
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(
                        Cipher.DECRYPT_MODE,
                        getOrCreateKey(),
                        new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
                byte[] plain = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP));
                return new String(plain, StandardCharsets.UTF_8).trim();
            } catch (Throwable ignored) {
                // If the device key was invalidated, the user can enter the API key again.
            }
        }

        String legacy = prefs.getString(LEGACY_KEY, "");
        legacy = legacy == null ? "" : legacy.trim();
        if (!legacy.isEmpty()) {
            if (saveApiKey(context, legacy)) return legacy;
        }
        return "";
    }

    static boolean saveApiKey(Context context, String value) {
        String clean = value == null ? "" : value.trim();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        if (clean.isEmpty()) {
            prefs.edit()
                    .remove(CIPHER_KEY)
                    .remove(IV_KEY)
                    .remove(LEGACY_KEY)
                    .apply();
            return true;
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(clean.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                    .putString(CIPHER_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(IV_KEY, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .remove(LEGACY_KEY)
                    .apply();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
