package dev.nagoor.payguard.core;

public record PaymentDecision(
    DecisionCategory category,
    boolean retryCandidate,
    boolean humanReviewRequired,
    String nextStep
) {}
