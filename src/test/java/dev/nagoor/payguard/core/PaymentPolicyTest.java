package dev.nagoor.payguard.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentPolicyTest {
    private final PaymentPolicy policy = new PaymentPolicy();

    @Test void transientFailureCanBeCandidateWithNoPriorRetries() {
        var result = policy.evaluate(PaymentState.FAILED, GatewayCode.TIMEOUT, 0);
        assertEquals(DecisionCategory.RETRY_CANDIDATE, result.category());
        assertTrue(result.retryCandidate());
        assertFalse(result.humanReviewRequired());
    }

    @Test void tooManyRetriesEscalates() {
        var result = policy.evaluate(PaymentState.FAILED, GatewayCode.RATE_LIMITED, 2);
        assertEquals(DecisionCategory.HUMAN_REVIEW, result.category());
        assertFalse(result.retryCandidate());
    }

    @Test void settledPaymentMustNeverBeRetried() {
        var result = policy.evaluate(PaymentState.SETTLED, GatewayCode.TIMEOUT, 0);
        assertEquals(DecisionCategory.RESOLVED, result.category());
        assertFalse(result.retryCandidate());
    }

    @Test void fraudNeedsHumanReview() {
        var result = policy.evaluate(PaymentState.FAILED, GatewayCode.FRAUD_REVIEW, 0);
        assertEquals(DecisionCategory.HUMAN_REVIEW, result.category());
        assertTrue(result.humanReviewRequired());
    }

    @Test void fraudReviewOverridesSettledState() {
        var result = policy.evaluate(PaymentState.SETTLED, GatewayCode.FRAUD_REVIEW, 0);
        assertEquals(DecisionCategory.HUMAN_REVIEW, result.category());
        assertTrue(result.humanReviewRequired());
        assertFalse(result.retryCandidate());
    }

    @Test void pendingPaymentMustNotBeRetried() {
        var result = policy.evaluate(PaymentState.PENDING, GatewayCode.TIMEOUT, 0);
        assertEquals(DecisionCategory.WAIT_AND_RECHECK, result.category());
        assertFalse(result.retryCandidate());
    }

    @Test void explicitDeclineMustNotBeRetried() {
        var result = policy.evaluate(PaymentState.FAILED, GatewayCode.DECLINED, 0);
        assertEquals(DecisionCategory.DO_NOT_RETRY, result.category());
        assertFalse(result.retryCandidate());
    }
}
