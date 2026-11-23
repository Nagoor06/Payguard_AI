package dev.nagoor.payguard.core;

import dev.nagoor.payguard.api.TriageRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Hashes idempotency keys; never logs or persists raw keys and never stores questions. */
public final class Idempotency {
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._-]{12,128}");
    private Idempotency() {}

    public static String keyHash(String key) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
        return sha256(key);
    }

    public static String fingerprint(TriageRequest r) {
        // Length prefixes prevent ambiguous concatenations. The question itself is never stored.
        return sha256(field(r.paymentState().name()) + field(r.gatewayCode().name())
            + field(r.retryCount().toString()) + field(r.question()));
    }

    private static String field(String value) { return value.length() + ":" + value; }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
