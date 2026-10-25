package com.ucentral.desarrollos.backendparkspotter.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generación de tokens aleatorios y hashes. En base de datos solo se guarda el hash del token,
 * nunca el token en claro (igual que los refresh tokens).
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Sin 0/O ni 1/I para que el código de reserva se pueda dictar o leer sin confusiones. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private SecureTokens() {
    }

    /** Token opaco url-safe con la cantidad de bytes aleatorios indicada. */
    public static String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /** Código corto legible (mayúsculas y dígitos sin caracteres ambiguos). */
    public static String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    public static String sha256Base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(value));
    }

    public static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    /** Comparación en tiempo constante (para firmas), para no filtrar información por tiempos. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
