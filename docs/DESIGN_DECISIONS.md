# Design decisions and trade-offs

1. **A deterministic policy decides; Bedrock explains.** The policy engine is a pure function over enumerated synthetic incident inputs. Bedrock's generated text is filtered and can fall back; no AI result can initiate a charge, retry, refund or review transition.
2. **Idempotency is backed by a database constraint.** A unique index on a SHA-256 hashed header key avoids in-memory coordination and handles multiple API replicas. A second SHA-256 fingerprint detects key reuse with a different payload. The raw key and full question are not stored. This is *idempotent triage-case creation*, not payment-provider idempotency.
3. **Review is a one-way, transactionally audited state change.** A conditional update guarantees that two reviewers cannot both complete the same pending case. The state update and immutable audit-event insert are in one Spring transaction. Review authorization uses a distinct shared credential; it would need real identity/RBAC/rotation for enterprise use.
4. **External inference occurs after case persistence.** The service can record a case even when Bedrock has an outage. Retries of the same triage request return the saved verdict without invoking Bedrock again. The original model text is intentionally not persisted, so a replay returns policy text rather than reproducing an identical LLM completion.
5. **Data minimization over fine-grained personalization.** No real transaction IDs, PANs, customer names, prompts, generated explanations or raw idempotency keys are stored. The input and output filters are limited heuristics; they do not confer PCI compliance.
6. **Operational visibility without cardinality explosion.** Generated `X-Request-ID` values support log correlation. Metrics use bounded labels (policy category and result), never caller-controlled text, IDs or key hashes. The Prometheus endpoint is intended for localhost development only.
7. **Test at the database boundary.** Unit tests cover the policy and safeguards; MockMvc tests cover HTTP semantics; Testcontainers uses actual PostgreSQL to check unique constraints, concurrent insertion and audit behavior. Local Docker and AWS verification remain outstanding.

## Potential next iterations

- Replace shared credentials with OIDC, scoped identities and a per-tenant authorization model.
- Add resilience features such as per-client rate limiting, retry budgets, latency histograms, circuit breakers and durable async AI explanation jobs.
- Implement formal threat modeling, DLP/PII tokenization, audit-retention policies and observability dashboards.
- Connect only a sandbox payment provider with state reconciliation, never infer successful payment actions from LLM text.
