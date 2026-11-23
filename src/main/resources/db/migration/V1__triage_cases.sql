-- Demonstration schema. No customer/payment identifiers, prompts or model output stored.
CREATE TABLE triage_cases (
    id UUID PRIMARY KEY,
    payment_state VARCHAR(24) NOT NULL,
    gateway_code VARCHAR(32) NOT NULL,
    retry_count INTEGER NOT NULL CHECK (retry_count BETWEEN 0 AND 10),
    category VARCHAR(40) NOT NULL,
    retry_candidate BOOLEAN NOT NULL,
    human_review_required BOOLEAN NOT NULL,
    next_step VARCHAR(280) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX triage_cases_created_at_idx ON triage_cases (created_at DESC);
