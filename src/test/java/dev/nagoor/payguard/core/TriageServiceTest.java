package dev.nagoor.payguard.core;

import dev.nagoor.payguard.api.ReviewRequest;
import dev.nagoor.payguard.api.TriageRequest;
import dev.nagoor.payguard.data.TriageRecord;
import dev.nagoor.payguard.data.TriageRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TriageServiceTest {
    private final TriageRepository repository = mock(TriageRepository.class);
    private TriageService service;
    private final UUID id = UUID.randomUUID();

    @BeforeEach void setUp() {
        reset(repository);
        service = new TriageService(new PaymentPolicy(), new InputSafety(),
            repository, new SimpleMeterRegistry());
    }

    @Test void checksInputSafetyBeforeDatabaseWrite() {
        var unsafe = new TriageRequest(PaymentState.FAILED, GatewayCode.TIMEOUT, 0,
            "customer@example.com");
        assertThrows(UnsafeInputException.class, () -> service.create("case-unique-2026", unsafe));
        verifyNoInteractions(repository);
    }

    @Test void createsWithHashedIdempotencyMaterial() {
        var input = new TriageRequest(PaymentState.FAILED, GatewayCode.TIMEOUT, 0, "Synthetic timeout");
        var record = new TriageRecord(id, "FAILED", "TIMEOUT", 0, "RETRY_CANDIDATE", true,
            false, "Reconcile before retry.", "NOT_REQUIRED", null, OffsetDateTime.now());
        when(repository.createIfAbsent(anyString(), anyString(), eq(input), any()))
            .thenReturn(new TriageRepository.CreateOutcome(record, true));
        assertTrue(service.create("case-unique-2026", input).created());
        verify(repository).createIfAbsent(eq(Idempotency.keyHash("case-unique-2026")),
            eq(Idempotency.fingerprint(input)), eq(input), any());
    }

    @Test void rejectsReviewOfClosedCase() {
        var record = new TriageRecord(id, "FAILED", "TIMEOUT", 0, "RETRY_CANDIDATE", true,
            false, "Reconcile before retry.", "NOT_REQUIRED", null, OffsetDateTime.now());
        when(repository.findById(id)).thenReturn(Optional.of(record));
        assertThrows(ReviewConflictException.class,
            () -> service.review(id, new ReviewRequest(ReviewAction.ACKNOWLEDGED, "ops-01")));
    }
}
