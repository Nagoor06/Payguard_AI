package dev.nagoor.payguard.data;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TriageAuditEvent(UUID id, UUID caseId, String eventType,
                                String reviewerRef, OffsetDateTime createdAt) {}
