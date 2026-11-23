package dev.nagoor.payguard.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExplanationGuardTest {
    private final ExplanationGuard guard = new ExplanationGuard();

    @Test void allowsBoundedPlainLanguageExplanation() {
        assertTrue(guard.acceptable("A gateway timeout needs reconciliation before any further action."));
    }
    @Test void rejectsClaimsOfCompletedFinancialActions() {
        assertFalse(guard.acceptable("We have charged the card."));
        assertFalse(guard.acceptable("Retry the payment now."));
    }
    @Test void rejectsSensitiveAndUnboundedResponses() {
        assertFalse(guard.acceptable("Contact client@example.com"));
        assertFalse(guard.acceptable("Card 4111 1111 1111 1111"));
        assertFalse(guard.acceptable("A".repeat(501)));
        assertFalse(guard.acceptable(""));
    }
}
