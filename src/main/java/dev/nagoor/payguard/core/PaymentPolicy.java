package dev.nagoor.payguard.core;

import org.springframework.stereotype.Component;

/**
 * Deterministic guidance only. NEVER initiates or authorizes a real payment retry.
 * A production payment service must independently verify provider state and
 * enforce idempotency before performing any financial operation.
 */
@Component
public final class PaymentPolicy {

    public PaymentDecision evaluate(PaymentState state, GatewayCode code, int previousRetries) {
        if (state == null || code == null || previousRetries < 0 || previousRetries > 10) {
            throw new IllegalArgumentException("Invalid payment incident parameters");
        }
        // Fraud signals require review even when a provider claims settlement.
        if (code == GatewayCode.FRAUD_REVIEW) {
            return new PaymentDecision(DecisionCategory.HUMAN_REVIEW, false, true,
                "Stop automated processing and request a risk-team review.");
        }
        if (state == PaymentState.SETTLED) {
            return new PaymentDecision(DecisionCategory.RESOLVED, false, false,
                "Payment already settled; never retry. Confirm status with the payment provider.");
        }
        if (state == PaymentState.PENDING) {
            return new PaymentDecision(DecisionCategory.WAIT_AND_RECHECK, false, false,
                "Do not retry a pending payment. Reconcile its status with the provider first.");
        }
        if (code == GatewayCode.DECLINED) {
            return new PaymentDecision(DecisionCategory.DO_NOT_RETRY, false, false,
                "Do not automatically retry a declined payment. Follow gateway decline guidance.");
        }
        if ((code == GatewayCode.TIMEOUT || code == GatewayCode.RATE_LIMITED) && previousRetries < 2) {
            return new PaymentDecision(DecisionCategory.RETRY_CANDIDATE, true, false,
                "Potential retry candidate ONLY after provider reconciliation and idempotency verification.");
        }
        return new PaymentDecision(DecisionCategory.HUMAN_REVIEW, false, true,
            "Escalate for human review; automatic retry is not recommended.");
    }
}
