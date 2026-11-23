package dev.nagoor.payguard.core;

import dev.nagoor.payguard.api.ReviewRequest;
import dev.nagoor.payguard.api.TriageRequest;
import dev.nagoor.payguard.data.TriageRecord;
import dev.nagoor.payguard.data.TriageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class TriageService {
    private final PaymentPolicy policy;
    private final InputSafety safety;
    private final TriageRepository repository;
    private final MeterRegistry metrics;

    public TriageService(PaymentPolicy policy, InputSafety safety, TriageRepository repository,
                         MeterRegistry metrics) {
        this.policy = policy;
        this.safety = safety;
        this.repository = repository;
        this.metrics = metrics;
    }

    @Transactional
    public TriageRepository.CreateOutcome create(String key, TriageRequest request) {
        safety.check(request.question());
        String hashedKey = Idempotency.keyHash(key);
        String fingerprint = Idempotency.fingerprint(request);
        PaymentDecision verdict = policy.evaluate(request.paymentState(), request.gatewayCode(), request.retryCount());
        var outcome = repository.createIfAbsent(hashedKey, fingerprint, request, verdict);
        // Bounded enumerations only: never use user input, case IDs or prompts as metric tags.
        metrics.counter("payguard.triage.requests", "result", outcome.created() ? "created" : "replayed",
            "category", outcome.record().category()).increment();
        return outcome;
    }

    @Transactional
    public TriageRecord review(UUID id, ReviewRequest request) {
        if (repository.findById(id).isEmpty()) throw new CaseNotFoundException();
        if (!repository.completeReview(id, request.action(), request.reviewerRef())) {
            throw new ReviewConflictException();
        }
        metrics.counter("payguard.triage.reviews", "action", request.action().name()).increment();
        return repository.findById(id).orElseThrow(CaseNotFoundException::new);
    }
}
