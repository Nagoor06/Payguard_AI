package dev.nagoor.payguard.ai;

import dev.nagoor.payguard.api.TriageRequest;
import dev.nagoor.payguard.core.PaymentDecision;

public interface InsightGenerator {
    Insight explain(TriageRequest request, PaymentDecision decision);
}
