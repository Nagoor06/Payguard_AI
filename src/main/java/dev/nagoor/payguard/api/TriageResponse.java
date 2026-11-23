package dev.nagoor.payguard.api;

import dev.nagoor.payguard.core.PaymentDecision;
import java.util.UUID;

public record TriageResponse(
    UUID caseId,
    PaymentDecision policy,
    String reviewStatus,
    String explanation,
    String explanationSource,
    String disclaimer
) {}
