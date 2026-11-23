package dev.nagoor.payguard.data;

import dev.nagoor.payguard.api.TriageRequest;
import dev.nagoor.payguard.core.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against a real PostgreSQL database when Docker is available (CI). */
@Testcontainers
class TriageRepositoryIntegrationTest {
    @Container static final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine");
    private static TriageRepository repository;

    @BeforeAll static void setup() {
        var source = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        repository = new TriageRepository(new JdbcTemplate(source));
    }

    @Test void sameIdempotencyKeyReturnsSameCaseAndOneAuditEvent() {
        var input = new TriageRequest(PaymentState.FAILED, GatewayCode.TIMEOUT, 0, "Synthetic timeout");
        var decision = new PaymentPolicy().evaluate(input.paymentState(), input.gatewayCode(), input.retryCount());
        String keyHash = Idempotency.keyHash("repeatable-case-2026"), fingerprint = Idempotency.fingerprint(input);
        var first = repository.createIfAbsent(keyHash, fingerprint, input, decision);
        var repeat = repository.createIfAbsent(keyHash, fingerprint, input, decision);
        assertTrue(first.created());
        assertFalse(repeat.created());
        assertEquals(first.record().id(), repeat.record().id());
        assertEquals(1, repository.events(first.record().id()).size());
    }

    @Test void keyReuseWithDifferentPayloadIsConflict() {
        var initial = new TriageRequest(PaymentState.FAILED, GatewayCode.TIMEOUT, 0, "Synthetic timeout");
        var changed = new TriageRequest(PaymentState.FAILED, GatewayCode.TIMEOUT, 1, "Synthetic timeout");
        var decision = new PaymentPolicy().evaluate(initial.paymentState(), initial.gatewayCode(), initial.retryCount());
        String key = Idempotency.keyHash("collision-case-2026");
        repository.createIfAbsent(key, Idempotency.fingerprint(initial), initial, decision);
        assertThrows(IdempotencyConflictException.class,
            () -> repository.createIfAbsent(key, Idempotency.fingerprint(changed), changed, decision));
    }

    @Test void reviewIsOneWayAndWritesAppendOnlyAudit() {
        var input = new TriageRequest(PaymentState.FAILED, GatewayCode.FRAUD_REVIEW, 0, "Synthetic flag");
        var decision = new PaymentPolicy().evaluate(input.paymentState(), input.gatewayCode(), input.retryCount());
        var created = repository.createIfAbsent(Idempotency.keyHash("review-case-2026"),
            Idempotency.fingerprint(input), input, decision);
        UUID caseId = created.record().id();
        assertEquals("PENDING_REVIEW", created.record().reviewStatus());
        assertTrue(repository.completeReview(caseId, ReviewAction.ESCALATED, "ops-01"));
        assertFalse(repository.completeReview(caseId, ReviewAction.ACKNOWLEDGED, "ops-02"));
        assertEquals("ESCALATED", repository.findById(caseId).orElseThrow().reviewStatus());
        assertEquals(2, repository.events(caseId).size());
        assertEquals("REVIEW_ESCALATED", repository.events(caseId).getLast().eventType());
    }

    @Test void concurrentRequestsWithSameKeyCreateOneCase() throws Exception {
        var input = new TriageRequest(PaymentState.FAILED, GatewayCode.TIMEOUT, 0, "Synthetic load test");
        var decision = new PaymentPolicy().evaluate(input.paymentState(), input.gatewayCode(), input.retryCount());
        String key = Idempotency.keyHash("parallel-" + UUID.randomUUID());
        String fingerprint = Idempotency.fingerprint(input);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            var jobs = new ArrayList<Future<TriageRepository.CreateOutcome>>();
            for (int i = 0; i < 6; i++) {
                jobs.add(executor.submit(() -> {
                    start.await();
                    return repository.createIfAbsent(key, fingerprint, input, decision);
                }));
            }
            start.countDown();
            var first = jobs.getFirst().get();
            int fresh = first.created() ? 1 : 0;
            for (int i = 1; i < jobs.size(); i++) {
                var result = jobs.get(i).get();
                if (result.created()) fresh++;
                assertEquals(first.record().id(), result.record().id());
            }
            assertEquals(1, fresh);
            assertEquals(1, repository.events(first.record().id()).size());
        }
    }
}
