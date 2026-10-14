package com.osmshop.auth;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TotpService {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec encryptionKey;

    public TotpService(@Value("${app.totp-key-file:../.local/totp.key}") String keyFile) {
        try {
            Path path = Path.of(keyFile).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                byte[] key = new byte[32];
                RANDOM.nextBytes(key);
                try { Files.write(path, key, StandardOpenOption.CREATE_NEW); }
                catch (FileAlreadyExistsException ignored) { /* Another process created the key. */ }
            }
            byte[] key = Files.readAllBytes(path);
            if (key.length != 32) throw new IllegalStateException("TOTP key must contain 32 bytes: " + path);
            encryptionKey = new SecretKeySpec(key, "AES");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load the local TOTP encryption key", exception);
        }
    }

    public String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public String otpauthUri(String email, String secret) {
        String label = URLEncoder.encode("FORME:" + email, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=FORME&algorithm=SHA1&digits=6&period=30";
    }

    public String encrypt(String secret) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
            ByteBuffer result = ByteBuffer.allocate(iv.length + encrypted.length);
            result.put(iv).put(encrypted);
            return Base64.getEncoder().encodeToString(result.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot encrypt TOTP secret", exception);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte[] iv = new byte[12];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot decrypt TOTP secret; restore the original .local/totp.key", exception);
        }
    }

    public long validStep(String secret, String code, Instant now, Long lastUsedStep) {
        if (code == null || !code.matches("[0-9]{6}")) return -1;
        long current = now.getEpochSecond() / 30;
        for (long step = current - 1; step <= current + 1; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) continue;
            if (MessageDigest.isEqual(code.getBytes(StandardCharsets.US_ASCII),
                    codeAtStep(secret, step).getBytes(StandardCharsets.US_ASCII))) return step;
        }
        return -1;
    }

    public String codeAtStep(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int value = ((digest[offset] & 0x7f) << 24) | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8) | (digest[offset + 3] & 0xff);
            return String.format(java.util.Locale.ROOT, "%06d", value % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot calculate TOTP", exception);
        }
    }

    static String encodeBase32(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) { bits -= 5; result.append(BASE32.charAt((buffer >>> bits) & 31)); }
        }
        if (bits > 0) result.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return result.toString();
    }

    static byte[] decodeBase32(String value) {
        ByteBuffer result = ByteBuffer.allocate(value.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char character : value.toCharArray()) {
            int digit = BASE32.indexOf(character);
            if (digit < 0) throw new IllegalArgumentException("Invalid TOTP secret");
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) { bits -= 8; result.put((byte) (buffer >>> bits)); }
        }
        return result.array();
    }
}
