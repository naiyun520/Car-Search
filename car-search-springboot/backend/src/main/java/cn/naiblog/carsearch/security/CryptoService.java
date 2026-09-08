package cn.naiblog.carsearch.security;

import cn.naiblog.carsearch.config.CarProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CryptoService {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 16;
    private final ObjectMapper mapper;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(ObjectMapper mapper, CarProperties properties) throws Exception {
        this.mapper = mapper;
        String secret = properties.dataEncryptKey() == null ? "" : properties.dataEncryptKey();
        if (secret.length() < 24) throw new IllegalStateException("DATA_ENCRYPT_KEY 未安全配置");
        this.key = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String encrypt(Object value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH * 8, iv));
            byte[] encryptedWithTag = cipher.doFinal(mapper.writeValueAsBytes(value));
            int cipherLength = encryptedWithTag.length - TAG_LENGTH;
            byte[] phpPayload = new byte[iv.length + encryptedWithTag.length];
            System.arraycopy(iv, 0, phpPayload, 0, iv.length);
            System.arraycopy(encryptedWithTag, cipherLength, phpPayload, iv.length, TAG_LENGTH);
            System.arraycopy(encryptedWithTag, 0, phpPayload, iv.length + TAG_LENGTH, cipherLength);
            return Base64.getEncoder().encodeToString(phpPayload);
        } catch (Exception error) {
            throw new IllegalStateException("敏感数据加密失败", error);
        }
    }

    public Map<String, Object> decrypt(String payload) {
        if (payload == null || payload.isBlank()) return Map.of();
        try {
            byte[] raw = Base64.getDecoder().decode(payload);
            if (raw.length < IV_LENGTH + TAG_LENGTH + 1) return Map.of();
            byte[] iv = java.util.Arrays.copyOfRange(raw, 0, IV_LENGTH);
            byte[] tag = java.util.Arrays.copyOfRange(raw, IV_LENGTH, IV_LENGTH + TAG_LENGTH);
            byte[] ciphertext = java.util.Arrays.copyOfRange(raw, IV_LENGTH + TAG_LENGTH, raw.length);
            byte[] javaPayload = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, javaPayload, 0, ciphertext.length);
            System.arraycopy(tag, 0, javaPayload, ciphertext.length, tag.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH * 8, iv));
            return mapper.readValue(cipher.doFinal(javaPayload), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
