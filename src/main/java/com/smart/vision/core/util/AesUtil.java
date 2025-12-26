package com.smart.vision.core.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.smart.vision.core.constant.CommonConstant.ALGORITHM;

/**
 * Utility class for AES encryption and decryption.
 * This class provides methods to encrypt and decrypt strings using AES algorithm.
 * The encryption key and initialization vector (IV) are injected from application properties.
 *
 * @author Ryan
 * @since 2025/12/25
 */

@Component
public class AesUtil {

    private static String key;

    private static String iv;

    @Value("${app.security.encrypt-key}")
    public void setKey(String key) {
        AesUtil.key = key;
    }

    @Value("${app.security.encrypt-iv}")
    public void setIv(String iv) {
        AesUtil.iv = iv;
    }

    /**
     * Encrypt string
     */
    public static String encrypt(String content) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(key);
            byte[] ivBytes = Base64.getDecoder().decode(iv);

            if (keyBytes.length != 32) throw new IllegalArgumentException("Key must be 32 bytes (AES-256)");
            if (ivBytes.length != 16) throw new IllegalArgumentException("IV must be 16 bytes");

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
}