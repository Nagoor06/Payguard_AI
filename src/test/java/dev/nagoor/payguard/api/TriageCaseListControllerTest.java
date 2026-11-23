package dev.nagoor.payguard.api;

import dev.nagoor.payguard.data.TriageRecord;
import dev.nagoor.payguard.data.TriageRepository;
import dev.nagoor.payguard.security.ApiKeyFilter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TriageCaseListControllerTest {
    private MockMvc mvc;
    private TriageRepository repository;

    @BeforeEach
    void setup() {
        repository = mock(TriageRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new TriageCaseListController(repository))
            .addFilters(new ApiKeyFilter("test-only-key"))
            .build();
    }

    @Test
    void listsRecentCasesWithAuthentication() throws Exception {
        var record = new TriageRecord(UUID.randomUUID(), "FAILED", "TIMEOUT", 0,
            "RETRY_CANDIDATE", true, false, "Reconcile before retrying.",
            "NOT_REQUIRED", null, OffsetDateTime.now());
        when(repository.recent(20)).thenReturn(List.of(record));

        mvc.perform(get("/api/v1/triage").header("X-API-Key", "test-only-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].gatewayCode").value("TIMEOUT"))
            .andExpect(jsonPath("$[0].reviewStatus").value("NOT_REQUIRED"));
        verify(repository).recent(20);
    }

    @Test
    void unauthorizedCallerCannotListCases() throws Exception {
        mvc.perform(get("/api/v1/triage")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    void caseListingIsBounded() throws Exception {
        mvc.perform(get("/api/v1/triage").header("X-API-Key", "test-only-key")
            .param("limit", "51"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/triage").header("X-API-Key", "test-only-key")
            .param("limit", "0"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
}
