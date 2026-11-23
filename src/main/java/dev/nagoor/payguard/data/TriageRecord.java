package dev.nagoor.payguard.data;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TriageRecord(
    UUID id,
    String paymentState,
    String gatewayCode,
    int retryCount,
    String category,
    boolean retryCandidate,
    boolean humanReviewRequired,
    String nextStep,
    String reviewStatus,
    OffsetDateTime reviewUpdatedAt,
    OffsetDateTime createdAt
) {}
