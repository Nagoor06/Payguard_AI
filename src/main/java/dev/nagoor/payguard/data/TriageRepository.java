package dev.nagoor.payguard.data;

import dev.nagoor.payguard.api.TriageRequest;
import dev.nagoor.payguard.core.IdempotencyConflictException;
import dev.nagoor.payguard.core.PaymentDecision;
import dev.nagoor.payguard.core.ReviewAction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Only policy metadata, hashed request identifiers and pseudonymous audit refs are persisted. */
@Repository
public class TriageRepository {
    private final JdbcTemplate jdbc;

    public TriageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CreateOutcome createIfAbsent(String hashedKey, String fingerprint,
                                        TriageRequest input, PaymentDecision decision) {
        UUID id = UUID.randomUUID();
        String status = decision.humanReviewRequired() ? "PENDING_REVIEW" : "NOT_REQUIRED";
        int inserted = jdbc.update("""
            INSERT INTO triage_cases
              (id, payment_state, gateway_code, retry_count, category, retry_candidate,
               human_review_required, next_step, request_key_hash, request_fingerprint, review_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (request_key_hash) WHERE request_key_hash IS NOT NULL DO NOTHING
            """, id, input.paymentState().name(), input.gatewayCode().name(), input.retryCount(),
            decision.category().name(), decision.retryCandidate(), decision.humanReviewRequired(),
            decision.nextStep(), hashedKey, fingerprint, status);
        if (inserted == 1) {
            recordEvent(id, "CASE_CREATED", null);
            return new CreateOutcome(findById(id).orElseThrow(), true);
        }
        // PostgreSQL's unique index serializes concurrent inserts for the same key.
        var rows = jdbc.query("""
            SELECT id, request_fingerprint FROM triage_cases WHERE request_key_hash = ?
            """, (rs, rowNum) -> new KeyRecord(
                rs.getObject("id", UUID.class), rs.getString("request_fingerprint")), hashedKey);
        if (rows.isEmpty() || !fingerprint.equals(rows.getFirst().fingerprint())) {
            throw new IdempotencyConflictException();
        }
        return new CreateOutcome(findById(rows.getFirst().id()).orElseThrow(), false);
    }

    public Optional<TriageRecord> findById(UUID id) {
        return jdbc.query("""
            SELECT id, payment_state, gateway_code, retry_count, category, retry_candidate,
                   human_review_required, next_step, review_status, review_updated_at, created_at
            FROM triage_cases WHERE id = ?
            """, (RowMapper<TriageRecord>) this::map, id).stream().findFirst();
    }

    /** Compare-and-set prevents double review and preserves an append-only audit record. */
    public boolean completeReview(UUID id, ReviewAction action, String reviewerRef) {
        int changed = jdbc.update("""
            UPDATE triage_cases SET review_status = ?, review_updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND review_status = 'PENDING_REVIEW'
            """, action.name(), id);
        if (changed == 1) {
            recordEvent(id, "REVIEW_" + action.name(), reviewerRef);
        }
        return changed == 1;
    }

    public List<TriageAuditEvent> events(UUID id) {
        return jdbc.query("""
            SELECT id, case_id, event_type, reviewer_ref, created_at
            FROM triage_audit_events WHERE case_id = ? ORDER BY created_at, id
            """, (rs, rowNum) -> new TriageAuditEvent(rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getString("event_type"),
                rs.getString("reviewer_ref"), rs.getObject("created_at", java.time.OffsetDateTime.class)), id);
    }

    private void recordEvent(UUID id, String type, String reviewer) {
        jdbc.update("""
            INSERT INTO triage_audit_events (id, case_id, event_type, reviewer_ref)
            VALUES (?, ?, ?, ?)
            """, UUID.randomUUID(), id, type, reviewer);
    }

    private TriageRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new TriageRecord(
            rs.getObject("id", UUID.class), rs.getString("payment_state"),
            rs.getString("gateway_code"), rs.getInt("retry_count"), rs.getString("category"),
            rs.getBoolean("retry_candidate"), rs.getBoolean("human_review_required"),
            rs.getString("next_step"), rs.getString("review_status"),
            rs.getObject("review_updated_at", java.time.OffsetDateTime.class),
            rs.getObject("created_at", java.time.OffsetDateTime.class));
    }

    private record KeyRecord(UUID id, String fingerprint) {}
    public record CreateOutcome(TriageRecord record, boolean created) {}
}
