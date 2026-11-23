# PayGuard AI

**Repository:** `payguard-ai`

**Payment incident triage with deterministic decision rules, idempotent APIs, human review, and optional Amazon Bedrock explanations.**

PayGuard AI is a Java Spring Boot backend for investigating **synthetic payment incidents** such as gateway timeouts, declined payments, rate limits, and fraud-review signals. It separates financial decision logic from generative AI: **the policy engine makes the decision; an optional LLM only explains it.**

> [!IMPORTANT]
> PayGuard is a software engineering demonstration, **not a payment processor**. It does not charge cards, retry transactions, issue refunds, transfer money, or connect to a real payment provider. Use **synthetic data only**. The current security controls are not a substitute for PCI DSS compliance or production financial-system security.

## Key features

| Area | Implementation |
| --- | --- |
| **Payment triage** | Deterministic policy for `FAILED`, `PENDING`, and `SETTLED` states, including timeout, rate-limit, decline, and fraud-review signals |
| **Retry-safe requests** | Required `Idempotency-Key`, SHA-256 key/request fingerprints, PostgreSQL unique constraints, same-case replay, and `409` on conflicting reuse |
| **Human review** | Restricted reviewer workflow with `PENDING_REVIEW` → `ACKNOWLEDGED` or `ESCALATED` transitions |
| **Auditability** | Append-only case and review events; transactional review state changes |
| **AI integration** | Optional AWS Bedrock Converse API via AWS SDK for Java v2, output screening, bounded requests, and deterministic fallback |
| **Security** | API-key protection, separate reviewer key, input validation, basic sensitive-input filtering, minimized stored data |
| **Observability** | Correlated request IDs, Actuator health probes, Micrometer metrics, authenticated Prometheus endpoint |
| **Quality** | JUnit 5, MockMvc, PostgreSQL Testcontainers integration tests, Docker Compose, GitHub Actions CI |

## Tech stack

**Java 21 · Spring Boot 3.5 · Spring JDBC · PostgreSQL 16 · Flyway · AWS SDK v2 · Amazon Bedrock (optional) · JUnit · Mockito · Testcontainers · Docker · GitHub Actions · Micrometer / Prometheus**

## Architecture

```text
                         Client / API consumer
                                 |
             X-API-Key + Idempotency-Key + JSON request
                                 |
                    Spring Boot REST API
                                 |
                    Validation & screening
                                 |
                      PaymentPolicy (Java)
                     /                  \
          PostgreSQL persistence      InsightGenerator
          + idempotency checks         /          \
          + audit event           Demo rules     AWS Bedrock
                     \                 /          /
                      \_______________/_________/
                                 |
                  Verdict + explanation source
                                 |
                Optional human-review endpoint
                 (additional X-Reviewer-Key)
                                 |
                  Transactional state change
                      + append-only audit
```

The generated explanation **cannot** override `PaymentPolicy`, approve financial operations, or mutate review status. Duplicate incident requests reuse the stored case without making a second AI call.

## Quick start — Windows / PowerShell

**Prerequisite:** Docker Desktop running with Linux containers enabled. You do **not** need a separate local PostgreSQL or Maven installation to run the app.

1. Clone or download this repository and open its root directory in PowerShell.
2. Create your local configuration:

   ```powershell
   Copy-Item .env.example .env
   notepad .env
   ```

3. Replace `APP_API_KEY`, `REVIEWER_API_KEY`, and `DB_PASSWORD` with **three different, strong secrets**. Set `AI_PROVIDER=demo` to run fully offline. Do not share or commit `.env`.
4. Start the API and database:

   ```powershell
   docker compose up --build -d
   docker compose ps
   Invoke-RestMethod http://localhost:8080/actuator/health
   ```

   Expected health response: `UP`. The application listens on **http://localhost:8080** and PostgreSQL on local port **5432**. Flyway applies schema migrations automatically at startup.

### Create your first incident

The following example reads the locally configured API key without printing it:

```powershell
$apiKey = (Get-Content .env | Where-Object { $_ -match '^APP_API_KEY=' } | Select-Object -First 1) -replace '^APP_API_KEY=', ''

$headers = @{
    'X-API-Key' = $apiKey
    'Idempotency-Key' = [guid]::NewGuid().ToString()
}

$body = @{
    paymentState = 'FAILED'
    gatewayCode = 'TIMEOUT'
    retryCount = 0
    question = 'Why did this synthetic payment time out?'
} | ConvertTo-Json

$response = Invoke-RestMethod -Method Post `
    -Uri 'http://localhost:8080/api/v1/triage' `
    -Headers $headers -ContentType 'application/json' -Body $body

$response | ConvertTo-Json -Depth 10
```

**Representative response** (identifiers vary):

```json
{
  "caseId": "<generated-uuid>",
  "policy": {
    "category": "RETRY_CANDIDATE",
    "retryCandidate": true,
    "humanReviewRequired": false,
    "nextStep": "Potential retry candidate ONLY after provider reconciliation and idempotency verification."
  },
  "reviewStatus": "NOT_REQUIRED",
  "explanation": "Demo explanation: Potential retry candidate ONLY after provider reconciliation and idempotency verification.",
  "explanationSource": "DEMO_RULES",
  "disclaimer": "Decision support only; no payment attempted. Synthetic data only."
}
```

### Verify persistence and idempotency

```powershell
# Load the saved incident from PostgreSQL-backed storage
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/triage/$($response.caseId)" `
    -Headers @{'X-API-Key' = $apiKey}

# Replay identical headers and request body; should reuse the same case ID
$replay = Invoke-RestMethod -Method Post `
    -Uri 'http://localhost:8080/api/v1/triage' `
    -Headers $headers -ContentType 'application/json' -Body $body

$replay.caseId -eq $response.caseId   # Expected: True
```

The first request returns **201 Created**. An identical replay returns **200 OK** with the same case ID. Reusing an idempotency key with a *different* request body returns **409 Conflict**.

### Human-review and audit workflow

Create a second incident using `gatewayCode = 'FRAUD_REVIEW'` and a new `Idempotency-Key`. It should have `reviewStatus = 'PENDING_REVIEW'`.

```powershell
$reviewerKey = (Get-Content .env | Where-Object { $_ -match '^REVIEWER_API_KEY=' } | Select-Object -First 1) -replace '^REVIEWER_API_KEY=', ''

$reviewHeaders = @{
    'X-API-Key' = $apiKey
    'X-Reviewer-Key' = $reviewerKey
}

# Replace <CASE_ID> with the fraud case's caseId
$caseId = '<CASE_ID>'
$reviewBody = @{ action = 'ESCALATED'; reviewerRef = 'ops-demo-01' } | ConvertTo-Json

Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8080/api/v1/triage/$caseId/review" `
    -Headers $reviewHeaders -ContentType 'application/json' -Body $reviewBody

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/triage/$caseId/events" `
    -Headers $reviewHeaders | ConvertTo-Json -Depth 10
```

The audit history contains `CASE_CREATED` and `REVIEW_ESCALATED`. Only `ACKNOWLEDGED` and `ESCALATED` are valid review actions. A second review transition is rejected. Reviewer references should be pseudonymous, not names or email addresses.

## API reference

| Method | Endpoint | Purpose | Access |
| --- | --- | --- | --- |
| `POST` | `/api/v1/triage` | Create or replay a synthetic triage case | `X-API-Key` + `Idempotency-Key` |
| `GET` | `/api/v1/triage/{id}` | Read stored case and review status | `X-API-Key` |
| `POST` | `/api/v1/triage/{id}/review` | Acknowledge or escalate a pending case | `X-API-Key` + `X-Reviewer-Key` |
| `GET` | `/api/v1/triage/{id}/events` | Read case audit history | `X-API-Key` + `X-Reviewer-Key` |
| `GET` | `/actuator/health` | Application health | Public (limited details) |
| `GET` | `/actuator/health/liveness` | Liveness probe | Public (limited details) |
| `GET` | `/actuator/health/readiness` | Readiness probe | Public (limited details) |
| `GET` | `/actuator/prometheus` | Metrics scrape | `X-API-Key` |

Request values: `paymentState` = `FAILED`, `PENDING`, `SETTLED`; `gatewayCode` = `TIMEOUT`, `RATE_LIMITED`, `DECLINED`, `FRAUD_REVIEW`, `UNKNOWN`, `NONE`; `retryCount` = integer from `0` to `10`.

See [`docs/openapi.yaml`](docs/openapi.yaml) for the full API contract.

## Tests

The test suite covers deterministic policy decisions, validation/sensitive-input safeguards, HTTP authorization, replay/conflict behavior, concurrent PostgreSQL idempotency, and human-review audit transitions.

**With Java 21, Maven, and Docker Desktop installed:**

```bash
mvn clean verify
```

**Without locally installed Maven (Windows PowerShell, Docker Desktop):**

```powershell
docker run --rm `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v "${PWD}:/workspace" -w /workspace `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -e TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock `
  maven:3.9-eclipse-temurin-21 mvn clean verify
```

> This command grants the temporary Maven container access to the Docker socket so that Testcontainers can run. Use it only with trusted local source code. The `TESTCONTAINERS_HOST_OVERRIDE` setting addresses Ryuk connectivity issues that can occur with nested Docker on Windows.

GitHub Actions runs the Maven verification suite on pull requests and pushes using `.github/workflows/ci.yml`.

### Verification status — November 23, 2026

The local workflow has been exercised successfully on Windows with Docker Desktop:

- Spring Boot started and returned a healthy status; PostgreSQL connectivity worked.
- Demo-mode incident creation, stored-case retrieval, and idempotent replay passed.
- Fraud-review escalation, audit retrieval, authentication checks, and protected Prometheus access passed.
- `mvn clean verify` completed with **BUILD SUCCESS** after correcting the Testcontainers/Ryuk host connectivity setting.
- **Live Bedrock inference is not yet verified.** In the attempted AWS environment, Nova Lite invocation returned `ValidationException: Operation not allowed`; PayGuard correctly served `POLICY_FALLBACK`.

These are local-development checks, not claims of production deployment, performance SLAs, or compliance certification.

## Optional AWS Bedrock integration

The default `AI_PROVIDER=demo` does **not** call AWS. To enable the implemented Bedrock adapter, configure:

```dotenv
AI_PROVIDER=bedrock
AWS_REGION=us-east-1
BEDROCK_MODEL_ID=amazon.nova-lite-v1:0
```

Supply credentials using an authorized AWS profile or IAM role, ensure the model is available and permitted in your AWS account/region, and give the identity the applicable `bedrock:InvokeModel` permission. **Do not put AWS access keys in Git or bake them into a Docker image.** AWS inference may incur charges.

When Bedrock returns an accepted explanation, `explanationSource` is `AWS_BEDROCK`. If invocation fails or its response is rejected, the deterministic policy remains available with `explanationSource = POLICY_FALLBACK`.

## Data protection and limitations

Only synthetic policy metadata, review states, event metadata, and hashed idempotency fingerprints are stored. Raw questions, generated text, card data, and raw idempotency keys are not persisted. Input screening is deliberately basic and **does not guarantee PII redaction**. API keys and a shared reviewer credential are appropriate only for a controlled local demonstration; real financial deployments would require identity-based authorization, managed secrets, threat modeling, comprehensive privacy and compliance controls, structured operational alerting, and secure infrastructure.

Future enhancements could include **sandbox payment-provider reconciliation**, realistic failure injection, retry budgets, authenticated operator identities, performance benchmarks, and AI explanation evaluation datasets.

## Project documentation

- [`docs/DESIGN_DECISIONS.md`](docs/DESIGN_DECISIONS.md) — engineering trade-offs and architectural rationale
- [`docs/QUALITY_REVIEW.md`](docs/QUALITY_REVIEW.md) — source-level quality assessment and planned improvements
- [`docs/openapi.yaml`](docs/openapi.yaml) — OpenAPI specification
- [`scripts/smoke.sh`](scripts/smoke.sh) — end-to-end shell smoke checks

## Stop the application

```powershell
docker compose down
```

Avoid `docker compose down -v` unless you intentionally want to delete the local PostgreSQL volume.
