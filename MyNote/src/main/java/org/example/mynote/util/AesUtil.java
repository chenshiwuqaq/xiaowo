package org.example.mynote.util;

import org.example.mynote.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 对称加密工具，用于 API Key 落库前的加密。
 *
 * <p>算法 AES/GCM/NoPadding：GCM 自带完整性校验，比 CBC 更稳妥；每次加密随机生成 12 字节 IV
 * 并拼接在密文头部一起 Base64 存储，因此同一个 Key 两次加密结果不同，不影响解密。
 * 主密钥由配置项经 SHA-256 派生为 256 bit。</p>
 *
 * @author MyNote
 */
@Component
public class AesUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final String ALGORITHM = "AES";

    private static final int IV_LENGTH = 12;

    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();

    private final SecretKeySpec keySpec;

    public AesUtil(AppProperties properties) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(properties.getCrypto().getAesKey().getBytes(StandardCharsets.UTF_8));
            this.keySpec = new SecretKeySpec(digest, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("初始化 API Key 加密器失败", e);
        }
    }

    /**
     * 加密明文。
     *
     * @param plain 明文，可为空
     * @return Base64(iv + 密文)，入参为空时返回 null
     */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /**
     * 解密密文。
     *
     * @param cipherText Base64(iv + 密文)
     * @return 明文，入参为空或解密失败时返回 null
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成掩码显示串，前端只看到首尾各若干字符。
     *
     * @param plain 明文 Key
     * @return 形如 sk-a****7890 的掩码；无 Key 时返回 null
     */
    public String mask(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        int keep = 4;
        if (plain.length() <= keep * 2) {
            return plain.charAt(0) + "****";
        }
        return plain.substring(0, keep) + "****" + plain.substring(plain.length() - keep);
    }
}
