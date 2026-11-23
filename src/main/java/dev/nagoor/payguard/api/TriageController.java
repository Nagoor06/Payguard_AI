package dev.nagoor.payguard.api;

import dev.nagoor.payguard.ai.Insight;
import dev.nagoor.payguard.ai.InsightGenerator;
import dev.nagoor.payguard.core.*;
import dev.nagoor.payguard.data.TriageAuditEvent;
import dev.nagoor.payguard.data.TriageRecord;
import dev.nagoor.payguard.data.TriageRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/triage")
public class TriageController {
    private static final Logger log = LoggerFactory.getLogger(TriageController.class);
    private final TriageService service;
    private final InsightGenerator insightGenerator;
    private final TriageRepository repository;

    public TriageController(TriageService service, InsightGenerator insightGenerator,
                            TriageRepository repository) {
        this.service = service;
        this.insightGenerator = insightGenerator;
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<TriageResponse> triage(
        @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody TriageRequest input) {
        var outcome = service.create(key, input);
        TriageRecord saved = outcome.record();
        PaymentDecision verdict = new PaymentDecision(DecisionCategory.valueOf(saved.category()),
            saved.retryCandidate(), saved.humanReviewRequired(), saved.nextStep());
        // On replay, neither call the LLM nor incur duplicate model cost; policy is replayed.
        Insight explanation = outcome.created()
            ? safelyExplain(input, verdict)
            : new Insight(saved.nextStep(), "REPLAY_POLICY");
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
            .header("X-Idempotent-Replay", Boolean.toString(!outcome.created()))
            .body(new TriageResponse(saved.id(), verdict, saved.reviewStatus(),
                explanation.explanation(), explanation.source(),
                "Decision support only; no payment attempted. Synthetic data only."));
    }

    /** Explanation generation must never turn a successfully persisted case into an HTTP 500. */
    private Insight safelyExplain(TriageRequest input, PaymentDecision decision) {
        try {
            Insight response = insightGenerator.explain(input, decision);
            return response != null && response.explanation() != null
                    && !response.explanation().isBlank()
                ? response : new Insight(decision.nextStep(), "POLICY_FALLBACK");
        } catch (RuntimeException ex) {
            // Deliberately exclude exception text, which could contain prompts or cloud details.
            log.warn("AI explanation unavailable; serving the deterministic policy response");
            return new Insight(decision.nextStep(), "POLICY_FALLBACK");
        }
    }

    @GetMapping("/{id}")
    public TriageRecord getCase(@PathVariable UUID id) {
        return repository.findById(id).orElseThrow(CaseNotFoundException::new);
    }

    @PostMapping("/{id}/review")
    public TriageRecord review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return service.review(id, request);
    }

    @GetMapping("/{id}/events")
    public List<TriageAuditEvent> events(@PathVariable UUID id) {
        if (repository.findById(id).isEmpty()) throw new CaseNotFoundException();
        return repository.events(id);
    }
}
