package dev.nagoor.payguard.api;

import dev.nagoor.payguard.ai.DemoInsightGenerator;
import dev.nagoor.payguard.ai.InsightGenerator;
import dev.nagoor.payguard.core.TriageService;
import dev.nagoor.payguard.data.TriageRecord;
import dev.nagoor.payguard.data.TriageRepository;
import dev.nagoor.payguard.security.ApiKeyFilter;
import dev.nagoor.payguard.security.ReviewerKeyFilter;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TriageControllerTest {
    private MockMvc mvc;
    private TriageRepository repository;
    private TriageService service;
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BODY = """
        {"paymentState":"FAILED", "gatewayCode":"TIMEOUT", "retryCount":0,
         "question":"Why did the synthetic gateway timeout?"}
        """;

    @BeforeEach void setup() {
        service = mock(TriageService.class);
        repository = mock(TriageRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new TriageController(service,
                new DemoInsightGenerator(), repository))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new ApiKeyFilter("test-only-key"), new ReviewerKeyFilter("test-reviewer-key"))
            .build();
    }

    private TriageRecord record(boolean review) {
        return new TriageRecord(ID, "FAILED", review ? "FRAUD_REVIEW" : "TIMEOUT", 0,
            review ? "HUMAN_REVIEW" : "RETRY_CANDIDATE", !review, review,
            "Reconcile before any action.", review ? "PENDING_REVIEW" : "NOT_REQUIRED",
            null, OffsetDateTime.now());
    }

    @Test void newCaseReturnsCreated() throws Exception {
        when(service.create(eq("case-unique-2026"), any())).thenReturn(
            new TriageRepository.CreateOutcome(record(false), true));
        mvc.perform(post("/api/v1/triage")
            .header("X-API-Key", "test-only-key")
            .header("Idempotency-Key", "case-unique-2026")
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-Idempotent-Replay", "false"))
            .andExpect(jsonPath("$.policy.category").value("RETRY_CANDIDATE"))
            .andExpect(jsonPath("$.reviewStatus").value("NOT_REQUIRED"))
            .andExpect(jsonPath("$.explanationSource").value("DEMO_RULES"));
    }

    @Test void replaySkipsModelCallAndReturnsSameId() throws Exception {
        when(service.create(anyString(), any())).thenReturn(
            new TriageRepository.CreateOutcome(record(false), false));
        mvc.perform(post("/api/v1/triage")
            .header("X-API-Key", "test-only-key")
            .header("Idempotency-Key", "case-unique-2026")
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.caseId").value(ID.toString()))
            .andExpect(jsonPath("$.explanationSource").value("REPLAY_POLICY"));
    }

    @Test void missingApiKeyRejected() throws Exception {
        mvc.perform(post("/api/v1/triage")
            .header("Idempotency-Key", "case-unique-2026")
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void missingIdempotencyHeaderRejected() throws Exception {
        mvc.perform(post("/api/v1/triage")
            .header("X-API-Key", "test-only-key")
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void missingRetryCountRejectedInsteadOfDefaultingToZero() throws Exception {
        mvc.perform(post("/api/v1/triage")
            .header("X-API-Key", "test-only-key")
            .header("Idempotency-Key", "case-unique-2026")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"paymentState\":\"FAILED\",\"gatewayCode\":\"TIMEOUT\",\"question\":\"test\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void humanReviewEndpointReturnsUpdatedCase() throws Exception {
        when(service.review(eq(ID), any())).thenReturn(record(true));
        mvc.perform(post("/api/v1/triage/" + ID + "/review")
            .header("X-API-Key", "test-only-key")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Reviewer-Key", "test-reviewer-key")
            .content("{\"action\":\"ACKNOWLEDGED\",\"reviewerRef\":\"ops-01\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.humanReviewRequired").value(true));
    }

    @Test void eventLookupOnUnknownCaseReturns404() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/triage/" + ID + "/events")
            .header("X-API-Key", "test-only-key")
            .header("X-Reviewer-Key", "test-reviewer-key"))
            .andExpect(status().isNotFound());
    }
    @Test void reviewRequiresSeparateReviewerCredential() throws Exception {
        mvc.perform(post("/api/v1/triage/" + ID + "/review")
            .header("X-API-Key", "test-only-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"ACKNOWLEDGED\",\"reviewerRef\":\"ops-01\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void aiFailureFallsBackWithoutLosingPersistedCase() throws Exception {
        InsightGenerator failed = mock(InsightGenerator.class);
        when(failed.explain(any(), any())).thenThrow(new IllegalStateException("model unavailable"));
        var fallbackMvc = MockMvcBuilders.standaloneSetup(
                new TriageController(service, failed, repository))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new ApiKeyFilter("test-only-key"), new ReviewerKeyFilter("test-reviewer-key"))
            .build();
        when(service.create(eq("case-unique-2026"), any())).thenReturn(
            new TriageRepository.CreateOutcome(record(false), true));
        fallbackMvc.perform(post("/api/v1/triage")
            .header("X-API-Key", "test-only-key")
            .header("Idempotency-Key", "case-unique-2026")
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.caseId").value(ID.toString()))
            .andExpect(jsonPath("$.explanationSource").value("POLICY_FALLBACK"));
    }

    @Test void auditEventsRequireReviewerCredential() throws Exception {
        mvc.perform(get("/api/v1/triage/" + ID + "/events")
            .header("X-API-Key", "test-only-key"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test void metricsEndpointRequiresApiKey() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isUnauthorized());
    }

}
