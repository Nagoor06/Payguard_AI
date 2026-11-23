package dev.nagoor.payguard.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputSafetyTest {
    private final InputSafety safety = new InputSafety();

    @Test void rejectsEmailAddress() {
        assertThrows(UnsafeInputException.class, () -> safety.check("Notify customer@example.com"));
    }

    @Test void rejectsCardLikeNumber() {
        assertThrows(UnsafeInputException.class, () -> safety.check("Card 4111 1111 1111 1111 failed"));
    }

    @Test void permitsSyntheticIncident() {
        assertDoesNotThrow(() -> safety.check("Gateway timeout - can this be retried?"));
    }
}
