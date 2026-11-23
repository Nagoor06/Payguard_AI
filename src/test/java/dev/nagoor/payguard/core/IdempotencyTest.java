package dev.nagoor.payguard.core;

import dev.nagoor.payguard.api.TriageRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdempotencyTest {
    @Test void stableHashWithoutKeepingRawKey() {
        String digest = Idempotency.keyHash("case-unique-2026");
        assertEquals(64, digest.length());
        assertEquals(digest, Idempotency.keyHash("case-unique-2026"));
        assertNotEquals(digest, "case-unique-2026");
    }

    @Test void invalidKeyIsRejected() {
        assertThrows(InvalidIdempotencyKeyException.class, () -> Idempotency.keyHash("short"));
        assertThrows(InvalidIdempotencyKeyException.class, () -> Idempotency.keyHash("bad key with spaces"));
    }

    @Test void anyMaterialInputDifferenceChangesFingerprint() {
        var initial = new TriageRequest(PaymentState.FAILED, GatewayCode.TIMEOUT, 0, "Synthetic timeout");
        var changed = new TriageRequest(PaymentState.FAILED, GatewayCode.TIMEOUT, 1, "Synthetic timeout");
        assertNotEquals(Idempotency.fingerprint(initial), Idempotency.fingerprint(changed));
        assertEquals(Idempotency.fingerprint(initial), Idempotency.fingerprint(initial));
    }
}
