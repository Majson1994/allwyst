package pl.allegrolister.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Szyfruje tokeny OAuth zapisywane w bazie (AES-256-GCM).
 * Klucz pochodzi z app.encryption-key (ustawiany przez {@link CryptoKeyInitializer}).
 */
@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "enc:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile SecretKeySpec key;

    static void init(String secret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            key = new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Nie można zainicjować klucza szyfrowania", e);
        }
    }

    private static SecretKeySpec key() {
        if (key == null) {
            init("zmien-ten-klucz-na-produkcji");
        }
        return key;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv).put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Błąd szyfrowania tokena", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || !dbData.startsWith(PREFIX)) {
            return dbData;
        }
        try {
            byte[] all = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
            byte[] plain = cipher.doFinal(all, 12, all.length - 12);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // zły klucz -> token nieczytelny, konto trzeba połączyć ponownie
            return null;
        }
    }
}
