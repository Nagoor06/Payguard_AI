package dev.nagoor.payguard.api;

import dev.nagoor.payguard.data.TriageRecord;
import dev.nagoor.payguard.data.TriageRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bounded, read-only case list for the local demo console.
 * The existing ApiKeyFilter protects all /api/* endpoints.
 */
@RestController
@RequestMapping("/api/v1/triage")
public class TriageCaseListController {
    private final TriageRepository repository;

    public TriageCaseListController(TriageRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<TriageRecord> recent(@RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 50");
        }
        return repository.recent(limit);
    }
}
