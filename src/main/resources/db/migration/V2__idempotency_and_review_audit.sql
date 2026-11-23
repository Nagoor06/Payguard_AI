-- Case creation has a retry-safe unique idempotency key, never a card/customer identifier.
ALTER TABLE triage_cases
    ADD COLUMN request_key_hash CHAR(64),
    ADD COLUMN request_fingerprint CHAR(64),
    ADD COLUMN review_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN review_updated_at TIMESTAMPTZ;

CREATE UNIQUE INDEX triage_cases_request_key_hash_uq ON triage_cases(request_key_hash)
    WHERE request_key_hash IS NOT NULL;

UPDATE triage_cases SET review_status = 'PENDING_REVIEW' WHERE human_review_required;

CREATE TABLE triage_audit_events (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES triage_cases(id),
    event_type VARCHAR(32) NOT NULL,
    reviewer_ref VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX triage_audit_events_case_created_idx ON triage_audit_events(case_id, created_at);
