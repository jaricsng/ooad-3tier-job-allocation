# REST API and Integration Patterns

The REST API (`/api/v1`) exposes the same system operations as the Job Allocation Board to other systems. This document explains the contract, the integration patterns it follows, and how to call it.

## 1. Contract first: OpenAPI 3.1

| Item | Where |
|---|---|
| The contract (source of truth) | `src/main/resources/api/openapi.yaml` |
| Served by the running app | `GET /api/v1/openapi.yaml` (authenticated), so clients always get the version that matches the code |
| Design rules as code | `api/.spectral.yaml`, run with `make api-lint` (Spectral 6.17, pinned by `api/package-lock.json`) |
| Code ⇄ contract check | `OpenApiContractTest`: every `/api` route is documented, every documented operation exists, and every DTO's fields equal its schema's properties |

**Workflow for an API change:**

1. Edit `openapi.yaml` first.
2. Run `make api-lint`.
3. Change the DTO, the controller and the route.
4. Run `make test`. The contract test fails until code and contract agree.

Breaking changes (removing or renaming a field, a new required field, changed semantics) go to `/api/v2`. Additive changes stay in v1.

Spectral house rules (beyond the OpenAPI recommended set):

| Rule | Why |
|---|---|
| `errors-use-problem-details` | Every 4xx/5xx is `application/problem+json`, so clients need one error parser |
| `paths-are-versioned` | Every path starts with `/api/v1/` |
| `post-accepts-idempotency-key` | Every non-idempotent operation documents the `Idempotency-Key` header |
| `operation-id-camel-case` | Stable names for code generators |

## 2. Integration patterns used

| Pattern | Source | Where | Benefit |
|---|---|---|---|
| **Problem Details** | RFC 9457 | `ProblemDTO`, `Response.problem`, `FrontController.error` | Machine-readable errors (`type`, `title`, `status`, `detail`, `instance`) that gateways and generic clients understand. Replaces the home-made `{status, message}` |
| **Correlation Identifier** | Enterprise Integration Patterns (EIP) | `Request.requestId()`, `X-Request-Id` header, access log, `requestId` in problems | One ID follows a request through the client's logs, the response and the server's log |
| **Idempotent Receiver** | EIP; IETF draft "The Idempotency-Key HTTP Header Field" | `IdempotentHandler` (a GoF **Decorator** around the route handler), `IdempotencyStore` | A client may safely retry a timed-out POST: the retry returns the stored response instead of booking the job twice |
| **Optimistic concurrency over HTTP** | RFC 9110 (`ETag`, `If-Match`, `If-None-Match`) | `ApiController.job`, `ApiController.allocate`, `ETags` | The job's `@Version` becomes its ETag. A stale `If-Match` is 412, so lost updates are prevented end to end |
| **Data Transfer Object** | PoEAA | `web.dto.*` records | A stable contract decoupled from the domain model; inbound DTOs reject unknown fields (mass-assignment protection) |
| **Resource linking** | REST / RFC 9110 | `Location` on `201 Created` → `GET /api/v1/allocations/{id}` | Clients follow the link instead of building URLs. Before this change, the link pointed to a route that did not exist |
| **Service versioning** | API design practice | `/api/v1` prefix, enforced by policy and by Spectral | Old clients keep working when v2 arrives |
| **Health Check API** | Microservice patterns | `GET /health` (public, no secrets) | Load balancers and the runbook can probe liveness and database reachability |

## 3. Calling the API

```bash
API=http://127.0.0.1:8080/api/v1
AUTH="supervisor:Supervisor#2026"

# Pending jobs (any signed-in role)
curl -s -u "$AUTH" "$API/jobs?priority=HIGH" | jq

# One job: note the ETag (its version). Demo IDs embed today's date: J-MMDD-nn
JOB="J-$(date +%m%d)-02"                                       # "DB backup verification", needs DBA
ETAG=$(curl -si -u "$AUTH" "$API/jobs/$JOB" | awk 'tolower($1)=="etag:" {print $2}' | tr -d '\r')
echo "$ETAG"                                                   # e.g. "0"

# Allocate with If-Match and an idempotency key (safe to retry with the same key)
curl -si -u "$AUTH" -X POST "$API/allocations" \
  -H 'Content-Type: application/json' -H "If-Match: $ETAG" \
  -H "Idempotency-Key: $(uuidgen)" -H 'X-Request-Id: demo-00000001' \
  -d "{\"jobId\":\"$JOB\",\"workerId\":\"W-03\"}"
# 201 Created, Location: /api/v1/allocations/…   (412 if the job changed meanwhile)

# An error is Problem Details
curl -s -u "$AUTH" "$API/jobs/NOPE"
# {"type":"/problems/not-found","title":"Not found","status":404,
#  "detail":"Job NOPE not found","instance":"/api/v1/jobs/NOPE","requestId":"…"}
```

Browser sessions may call the API too. State-changing calls then need the `X-CSRF-Token` header. Basic-auth clients don't.

## 4. Status codes

| Status | Meaning in this API |
|---|---|
| 200 / 201 / 204 | Success; 201 carries `Location` |
| 304 | `If-None-Match` matched: the job is unchanged |
| 400 | Malformed input (JSON, date, enum, header format) |
| 401 | Missing or wrong credentials (`WWW-Authenticate: Basic`) |
| 403 | Role not allowed (VIEWER calling a SUPERVISOR operation), or CSRF token missing |
| 404 | Unknown ID or path |
| 405 | Path exists, method doesn't |
| 409 | Stale `jobVersion`, a concurrent change, or the same `Idempotency-Key` still running |
| 412 | `If-Match` no longer matches the job |
| 413 | Body over 64 KB |
| 422 | Business rule failed (skills, capacity, job not pending), or an `Idempotency-Key` reused with another body |
| 500 | Unexpected error; `detail` contains the request ID to find in the server log |

The mapping lives in one table: `ErrorMapper`.

## 5. Known limits and next steps

| Topic | Current state | Next step for production |
|---|---|---|
| Idempotency store | In memory, per process, 24 h | A shared table or Redis when there is more than one instance |
| Pagination | Lists are small (one day of jobs) | Cursor pagination (`limit` + `next` link) before lists grow |
| Rate limiting | None | `429 Too Many Requests` with `Retry-After` at the gateway |
| Events to other systems | Listeners run in-process | **Transactional Outbox** + message relay (EIP Guaranteed Delivery) for e-mail or other services |
| Long operations | Auto-allocate is synchronous | `202 Accepted` + status resource if it ever takes seconds |
| API console | Spec served as YAML | Swagger UI or Redoc pointed at `/api/v1/openapi.yaml` |
