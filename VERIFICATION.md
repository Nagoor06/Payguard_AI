# Verification record — PayGuard AI v0.2

## Confirmed in this session

- Java 21 runtime/compiler present.
- **28 isolated checks passed** using `javac` and `java` against the unmodified production `PaymentPolicy`, `InputSafety`, `Idempotency` and `ExplanationGuard` implementations, with lightweight stubs only for external annotations and the request record. Cases cover policy outcomes, retry limits, sensitive-input checks, SHA-256 key hashing, fingerprint differences, and bounded/unsafe model text.
- Maven `pom.xml` parses as valid XML.
- Source tree, migrations, configuration, tests and documentation were packaged into ZIP.
- Resume PDF was compiled and visually inspected separately.

## Not yet confirmed

- **Maven and Docker are not installed in this execution environment.** Therefore a complete Spring Boot build, full JUnit / MockMvc suite and PostgreSQL Testcontainers tests have **not been run** locally.
- No HTTP service or PostgreSQL database was launched locally.
- AWS credentials and authorized Bedrock model access were not provided; **no actual Bedrock invocation** has been demonstrated.
- GitHub Actions workflow was authored but not executed in a repository.

## Reproduce on a prepared machine

```bash
mvn -B -ntp clean verify
cp .env.example .env
# use secure random values for APP_API_KEY and REVIEWER_API_KEY
docker compose up --build
```

Use the README's cURL requests to verify the 201 → 200 replay behavior, 409 mismatched keys, human-review-only actions, audit events, Prometheus metrics and offline/Bedrock explanation sources. Check the actual CI results before stating the complete test suite passes.


## v0.3 source review (2026-11-23)

The following additional code-level corrections were made after the previous report:
- Added missing Testcontainers PostgreSQL + JUnit 5 and Prometheus-registry Maven dependencies.
- Added API-key auth for Prometheus and reviewer-key auth for audit history, including MockMvc tests.
- Added controller-level model failure fallback and settlement/fraud policy precedence test.
- Updated the smoke script and OpenAPI security contract.

**Important:** No Maven CLI or Docker engine was available during this review, so a full compile, MockMvc suite, PostgreSQL integration test run, Docker image build and actual Bedrock invocation remain unverified.

### Isolated review checks

Java 21 successfully compiled framework-independent Java policy/security classes against a stub annotation and ran **1,197 positive invariant/assertion checks** (state matrix + input/output screening + request hashing). This does not validate Spring context initialization or PostgreSQL integration. See `docs/QUALITY_REVIEW.md` for coverage limits.
