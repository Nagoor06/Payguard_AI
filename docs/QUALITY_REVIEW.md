# PayGuard AI — Quality and engineering review

Review date: 2026-11-23 · Version: 0.3.0

## Corrections incorporated

| Priority | Finding | Change | Validation status |
|---|---|---|---|
| P0 | PostgreSQL integration tests reference Testcontainers without Maven dependencies. | Declared `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` test dependencies (versions managed by Spring Boot 3.5). | XML and dependency coordinates checked; full Maven verification outstanding. |
| P0 | Prometheus endpoint advertised, but registry was absent. | Added `micrometer-registry-prometheus`. | Source/config validation; runtime scrape outstanding. |
| P1 | Operational metrics and reviewer audit history lacked suitable access boundaries. | API key protects `/actuator/prometheus`; audit history requires the reviewer key in addition to the API key. | Added MockMvc tests and smoke assertions; Spring runtime suite outstanding. |
| P1 | Explanation service error after database insertion could turn a successful triage into a 500 response. | Added controller fallback to deterministic policy and Bedrock runtime fallback, with a generic warning that does not log prompts. | Added controller unit/HTTP test; full suite outstanding. |
| P1 | Fraud-review code on a settled case was silently treated as resolved. | Risk review now takes precedence over settlement status; financial operations still cannot be executed. | Executed isolated policy checks and added JUnit regression test. |
| P2 | Reviewer, API, and request-ID filters had ambiguous order. | Set explicit filter priorities: request ID, API key, reviewer key. | Source-level review only. |
| P2 | Smoke script and OpenAPI security contract did not match new reviewer protections. | Updated script, OpenAPI and README; health readiness/liveness probes enabled. | `bash -n` and YAML parsing passed. |

## Independent checks completed

- Validated `pom.xml` as well-formed XML and parsed the Spring, Docker Compose, OpenAPI and CI YAML files.
- Shell syntax check passed for `scripts/smoke.sh`.
- Java 21 compiled the framework-independent policy/security classes against a minimal annotation stub.
- An isolated Java harness passed **1,197 checks**, including 198 state/code/retry-count combinations and their invariants, fraud handling, card/email screening and deterministic idempotency hashes.

## Not yet verified

No Maven CLI or Docker daemon was available in the review environment. Consequently **the full Spring Boot build, MockMvc tests, Testcontainers PostgreSQL integration/concurrency tests, Docker image and Compose startup, live Prometheus scrape, and real AWS Bedrock invocation have not been executed or certified.** The added tests and integrations should be run locally or in GitHub Actions before claiming them as passed.

## Next steps with genuine engineering value

1. **Run `mvn clean verify`** with Java 21 and Docker; fix any actual compilation and integration issues. This is more important than adding a new framework.
2. **Add realistic provider reconciliation** through a deterministic mock payment gateway with a sandbox-only state machine. Keep provider-side writes explicitly separate from the advisory triage service.
3. **Adopt managed auth and secrets** (OIDC/JWT roles, IAM and secret rotation), request body limits, distributed rate limiting, and cross-tenant authorization if moving beyond local testing.
4. **Add an outbox and background inference worker** only if volume or latency data demonstrates a need; instrument p95 latency, fallback rate and Bedrock token/cost usage in a tested environment.
5. **Demonstrate AWS Bedrock** using a permitted model and IAM role, then record a reproducible masked demo, model performance and cost observations. Never send real financial/PII data into this synthetic service.

A runnable demo and successful CI pipeline are worth more on a resume than unverified technology keywords.
