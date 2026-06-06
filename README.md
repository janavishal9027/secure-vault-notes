# Notes Service (`notes`)

The notes service is the notes-domain backend of the **Digital Notes / secure-vault** platform. It owns the full lifecycle of a user's notes — create, read, update (full and partial), pin/unpin, archive/unarchive, delete — plus two AI-assisted features: on-demand note summarization and voice-to-text transcription. Every request carries the shared JWT issued by the **Authentication** service; the notes service does not mint or self-verify tokens, instead it delegates validation to Authentication over a Feign client and scopes all data access to the `ownerUserId` extracted from that token. Note changes are published to Kafka so the AI tier can keep vector embeddings in sync, and AI summaries flow back asynchronously over Kafka and are persisted onto the note.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 21 |
| Framework | Spring Boot 4.0.6 (Spring Web MVC) |
| Cloud | Spring Cloud 2025.1.1 (OpenFeign) |
| Persistence | Spring Data JPA / Hibernate, PostgreSQL |
| Messaging | Apache Kafka (`spring-boot-starter-kafka`) |
| Validation | Jakarta Bean Validation (`spring-boot-starter-validation`) |
| Mapping | ModelMapper 3.2.4 |
| API docs | springdoc-openapi (Swagger UI) 3.0.3 |
| AI integrations | OpenAI Whisper (transcription), ai-core-service (embeddings), ai-worker (summaries) |
| Boilerplate | Lombok |
| Build | Maven (wrapper included) |
| Container | Multi-stage Docker (`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`) |

## How it fits in the platform

The notes service sits behind the platform's nginx/Traefik ingress on the `/notes` path. It validates JWTs against Authentication on every API call and talks to the Python AI tier both synchronously (via `RestTemplate`) and asynchronously (via Kafka).

```
                 Bearer JWT
   Frontend ───────────────► notes-service (/notes, :3213)
   (:3000)                      │
                               │ Feign: validate token + extract userId
                               ▼
                        Authentication service
                               │
   notes-service ─────────────►│  publishes / consumes Kafka events
        │                      ▼
        │              ┌──────────────────────────────────────┐
        │   Kafka      │ notes.lifecycle  (CREATED/UPDATED/    │
        ├─────────────►│                   DELETED)            │
        │              │ notes.summary.request                 │
        │              │ ai.summary.ready                      │
        │              └──────────────────────────────────────┘
        │                      │
        │  HTTP (RestTemplate) │ consumed by this service's own listeners
        ├──────────────────────┴─► ai-core-service (:8001)  /embed, /embed/{id}
        └─────────────────────────► ai-worker      (:8000)  /summarize
                                  └► OpenAI Whisper          /v1/audio/transcriptions
```

Notable: since the old `AIGateway` hop was removed, the notes service **consumes its own Kafka events**. `NoteIndexerConsumer` listens on `notes.lifecycle` and calls ai-core-service to (re)index or delete embeddings; `SummaryRequestConsumer` listens on `notes.summary.request`, calls ai-worker, and re-publishes the result to `ai.summary.ready`, which `SummaryEventConsumer` persists back onto the note.

## Running locally

### Prerequisites

- JDK 21
- A running PostgreSQL instance with a `digital-notes` database (schema `secure-vault`)
- A running Kafka broker
- A running **Authentication** service (for token validation; the notes API rejects all requests otherwise)
- Optional for AI features: ai-core-service, ai-worker, and an OpenAI API key (transcription)

### Environment variables

All config is externalized in [application.yml](src/main/resources/application.yml). The placeholders below must be supplied (a checked-in `application-local.yml` provides local-dev defaults).

| Variable | Required | Used for | Example / default |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | yes | JDBC URL for PostgreSQL | `jdbc:postgresql://localhost:5432/digital-notes?currentSchema=secure-vault` |
| `SPRING_DATASOURCE_USERNAME` | yes | DB user | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | yes | DB password | — |
| `KAFKA_BOOTSTRAP_SERVERS` | yes | Kafka broker(s) | `localhost:9092` |
| `JWT_SECRET_KEY` | yes | JWT signing secret (must match Authentication) | — |
| `JWT_EXPIRATION` | yes | JWT expiry in ms | `600000` |
| `AUTHENTICATION_SERVICE_URL` | yes | Base URL of Authentication for the Feign client | `http://localhost:3211/authentication` |
| `AI_CORE_BASE_URL` | for indexing | ai-core-service base URL (embeddings) | `http://localhost:8001` |
| `AI_WORKER_BASE_URL` | for summaries | ai-worker base URL (summarization) | `http://localhost:8000` |
| `OPENAI_API_KEY` | for transcription | OpenAI Whisper key; empty disables `/transcribe` | _(empty)_ |

> Note: the deploy manifest [deployment.yml](deployment.yml) currently injects the JWT settings as `SECURITY_JWT_SECRET_KEY` / `SECURITY_JWT_EXPIRATION_TIME`, whereas [application.yml](src/main/resources/application.yml) reads `JWT_SECRET_KEY` / `JWT_EXPIRATION` (binding `jwt.secret-key` / `jwt.expiration-time`). When deploying, ensure the secret reaches the property the app actually binds.

### Port and context-path

- Port: **3213**
- Context-path: **`/notes`**
- So endpoints resolve at `http://localhost:3213/notes/api/notes/...`
- Swagger UI: `http://localhost:3213/notes/swagger.html`

### Start commands

Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run
```

Linux / macOS:

```bash
./mvnw spring-boot:run
```

## API overview

All endpoints are under context-path `/notes`, so the full path is `/notes` + the path below. Every route matches `/api/**` and therefore passes through `AuthenticationInterceptor` — a valid `Authorization: Bearer <JWT>` header is **required**; the interceptor validates the token against Authentication and injects `ownerUserId` into the request. There are **no public endpoints and no role-restricted (`@PreAuthorize`) endpoints** in this service — authorization is purely owner-scoped: a user can only act on notes whose `ownerUserId` matches their token.

### NotesController — [NotesController.java](src/main/java/com/application/notes/controller/NotesController.java) (base `/api/notes`)

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| POST | `/api/notes/createdNote` | Bearer JWT | Create a note from `{title, content, tags}`; publishes a `CREATED` lifecycle event. |
| GET | `/api/notes/getNotes/{noteId}` | Bearer JWT | Fetch a single note owned by the caller. |
| GET | `/api/notes/owner/allNotes` | Bearer JWT | List the caller's active, non-archived notes. |
| GET | `/api/notes/owner/pinned` | Bearer JWT | List the caller's pinned (active) notes. |
| GET | `/api/notes/owner/archived` | Bearer JWT | List the caller's archived (active) notes. |
| PUT | `/api/notes/updateNote?noteId=` | Bearer JWT | Full update of title/content/pinned/archived/tags; publishes `UPDATED` only if content changed. |
| PATCH | `/api/notes/updateNotePartial?noteId=` | Bearer JWT | Partial update (only non-null fields); publishes `UPDATED` only if content changed. |
| PATCH | `/api/notes/pinNote?noteId=` | Bearer JWT | Pin a note. |
| PATCH | `/api/notes/unpinNote?noteId=` | Bearer JWT | Unpin a note. |
| PATCH | `/api/notes/archiveNote?noteId=` | Bearer JWT | Archive a note. |
| PATCH | `/api/notes/unarchiveNote?noteId=` | Bearer JWT | Unarchive a note. |
| DELETE | `/api/notes/deleteNote?noteId=` | Bearer JWT | Delete a note; publishes a `DELETED` lifecycle event (triggers embedding removal). |
| POST | `/api/notes/summarize/{noteId}` | Bearer JWT | Request an AI summary (content must be ≥ 50 chars); sets `summaryStatus=PENDING` and publishes a summary-request event. |
| POST | `/api/notes/transcribe` | Bearer JWT | Multipart upload (`file`, optional `language`); transcribes audio via OpenAI Whisper (≤ 25 MB). Returns 500 if `OPENAI_API_KEY` is unset. |

## Security model

This service has **no Spring Security `SecurityConfiguration`**. Authentication is enforced by a servlet `HandlerInterceptor` registered for `/api/**`:

- [WebInterceptorConfiguration.java](src/main/java/com/application/notes/configuration/WebInterceptorConfiguration.java) registers the interceptor on `addPathPatterns("/api/**")`.
- [AuthenticationInterceptor.java](src/main/java/com/application/notes/configuration/AuthenticationInterceptor.java) requires an `Authorization: Bearer <token>` header, lets `OPTIONS` preflight through, then:
  1. calls `AuthenticationClient.validateToken(token)` — must return `true`;
  2. calls `AuthenticationClient.extractUserId(token)` and stores it as the `ownerUserId` request attribute.
  Any failure raises `AuthenticationException` (mapped by the global exception handler).
- [AuthenticationClient.java](src/main/java/com/application/notes/feignService/AuthenticationClient.java) is the Feign client (`@FeignClient(name="Authentication", url="${digital.app.authentication}", path="/api/user")`) backing validation, hitting `/public/validate` and `/public/extractUserId`.
- CORS is configured in [CorsConfiguration.java](src/main/java/com/application/notes/configuration/CorsConfiguration.java) for origin `http://localhost:3000` with credentials.
- Swagger declares a `bearerAuth` HTTP/JWT security scheme — see [SwaggerConfiguration.java](src/main/java/com/application/notes/configuration/SwaggerConfiguration.java).

Ownership is enforced in the service layer: every read/write resolves the note via `findByNoteIdAndOwnerUserId(...)` (or owner-scoped list queries), so cross-user access yields a `ResourceNotFoundException`/`AccessDeniedException`.

## Data model

### `Notes` — [Notes.java](src/main/java/com/application/notes/model/Notes.java) (JPA `@Entity`)

The single persisted entity. Key fields:

| Field | Type | Notes |
| --- | --- | --- |
| `noteId` | String (`@Id`) | Generated as `"NOTE" + year + dayOfYear + second + nano`. |
| `title` | String | Required, ≤ 200 chars (DTO validation). |
| `content` | String (`TEXT`) | Required. |
| `ownerUserId` | String | Owner from the JWT; scopes all access. |
| `pinned` | boolean | Defaults `false`. |
| `archived` | boolean | Defaults `false`. |
| `tags` | String | Free-form, ≤ 300 chars. |
| `status` | String | `ACTIVE` on create (soft-state flag). |
| `summary` | String (`TEXT`) | AI-generated summary text, filled asynchronously. |
| `summaryStatus` | String(16) | `NONE` → `PENDING` → `READY` / `FAILED`. |
| `createdAt` / `updatedAt` | LocalDateTime | Hibernate `@CreationTimestamp` / `@UpdateTimestamp`. |

`Users` ([Users.java](src/main/java/com/application/notes/model/Users.java)) is **not** a JPA entity — it is a plain DTO used only to deserialize responses from the Feign `AuthenticationClient.getUserByUsername`.

DTOs: [NotesRequestDto.java](src/main/java/com/application/notes/dtos/NotesRequestDto.java) (validated input), [NotesResponseDto.java](src/main/java/com/application/notes/dtos/NotesResponseDto.java), [TranscriptionResponseDto.java](src/main/java/com/application/notes/dtos/TranscriptionResponseDto.java). Repository: [NotesRepository.java](src/main/java/com/application/notes/repository/NotesRepository.java).

## Messaging & service-to-service calls

Kafka topic names are centralized in [KafkaTopics.java](src/main/java/com/application/notes/configuration/KafkaTopics.java). Producer JSON serialization is configured in [KafkaProducerConfig.java](src/main/java/com/application/notes/configuration/KafkaProducerConfig.java) and consumers in `KafkaConsumerConfig.java`. The base consumer group is `notes-service`, with per-listener suffixes.

### Topics

| Topic | Direction | Event | Trigger / handler |
| --- | --- | --- | --- |
| `notes.lifecycle` | produce | `NoteEvent` (CREATED / UPDATED / DELETED) | Published on create / content-change update / delete by [NoteEventProducer.java](src/main/java/com/application/notes/service/NoteEventProducer.java). |
| `notes.lifecycle` | consume | `NoteEvent` | [NoteIndexerConsumer.java](src/main/java/com/application/notes/service/NoteIndexerConsumer.java) → calls ai-core-service `/embed` (create/update) or `/embed/{noteId}` DELETE (delete). |
| `notes.summary.request` | produce | `SummaryRequestedEvent` | Published by `requestSummary` via `NoteEventProducer`. |
| `notes.summary.request` | consume | `SummaryRequestedEvent` | [SummaryRequestConsumer.java](src/main/java/com/application/notes/service/SummaryRequestConsumer.java) → calls ai-worker `/summarize`, then publishes the result. |
| `ai.summary.ready` | produce | `SummaryReadyEvent` (READY / FAILED) | [SummaryReadyProducer.java](src/main/java/com/application/notes/service/SummaryReadyProducer.java). |
| `ai.summary.ready` | consume | `SummaryReadyEvent` | [SummaryEventConsumer.java](src/main/java/com/application/notes/service/SummaryEventConsumer.java) → persists `summary` + `summaryStatus` onto the note. |

### Synchronous calls — [AiClient.java](src/main/java/com/application/notes/feignService/AiClient.java)

A `RestTemplate`-based client (invoked from the Kafka consumers above):

- `embed(...)` → `POST {AI_CORE_BASE_URL}/embed`
- `deleteEmbeddings(noteId)` → `DELETE {AI_CORE_BASE_URL}/embed/{noteId}`
- `summarize(...)` → `POST {AI_WORKER_BASE_URL}/summarize`

Transcription ([TranscriptionServiceImpl.java](src/main/java/com/application/notes/service/TranscriptionServiceImpl.java)) calls OpenAI Whisper directly (`https://api.openai.com/v1/audio/transcriptions`, model `whisper-1`, ≤ 25 MB, `verbose_json` response).

## Build, test & package

```bash
# Run unit/integration tests
./mvnw test

# Build the executable jar (skip tests)
./mvnw clean package -DskipTests
```

On Windows use `.\mvnw.cmd` in place of `./mvnw`. The build produces `target/notes-0.0.1-SNAPSHOT.jar`, runnable with `java -jar target/notes-*.jar`.

## Docker

A multi-stage [Dockerfile](Dockerfile) builds with Maven and runs on a JRE-only image, exposing port `3213`:

```bash
docker build -t secure-vault-notes:local .
docker run --rm -p 3213:3213 \
  -e SPRING_DATASOURCE_URL=... \
  -e SPRING_DATASOURCE_USERNAME=... \
  -e SPRING_DATASOURCE_PASSWORD=... \
  -e KAFKA_BOOTSTRAP_SERVERS=... \
  -e JWT_SECRET_KEY=... -e JWT_EXPIRATION=600000 \
  -e AUTHENTICATION_SERVICE_URL=... \
  -e AI_CORE_BASE_URL=... -e AI_WORKER_BASE_URL=... \
  -e OPENAI_API_KEY=... \
  secure-vault-notes:local
```

The image accepts `GIT_COMMIT`, `BUILD_NUMBER`, and `BUILD_DATE` build args, surfaced as OCI image labels.

## Deployment

Deployment targets a `secure-vault-*` k3s cluster fronted by host nginx → Traefik. The flow is "render manifests locally → scp → run remote script":

- [ci/deploy.sh](ci/deploy.sh) renders the k8s manifests + nginx snippet (env-var substitution via `sed`), ships them and `deploy-remote.sh` over SSH, and executes the remote script. It documents all required deploy-time variables (`VPS_USER`, `VPS_HOST`, `REMOTE_DIR`, `LXD_CONTAINER`, `KUBE_NAMESPACE`, `APP_NAME`, `IMAGE_REPO`, `IMAGE_TAG`, `INGRESS_HOST`, `LXD_BRIDGE_IP`, `DB_URL`, `DB_PASSWORD`, `JWT_SECRET`, `KAFKA_BOOTSTRAP_SERVERS`, `AUTHENTICATION_SERVICE_URL`, `AI_CORE_BASE_URL`, `AI_WORKER_BASE_URL`) and optional ones (`DB_USERNAME`, `REPLICAS`, `JWT_EXPIRATION`, `OPENAI_API_KEY`, `SWAGGER_ENABLED`).
- [ci/deploy-remote.sh](ci/deploy-remote.sh) runs inside the LXD host to apply the manifests into the cluster.
- [ci/nginx/notes.location.conf](ci/nginx/notes.location.conf) is the host nginx `location /notes/` snippet (25 MB body limit for audio uploads, 120 s read timeout for Whisper).

### Kubernetes manifests (templated)

- [deployment.yml](deployment.yml) — Deployment (containerPort `3213`, TCP readiness/liveness probes, env injection, JVM heap caps, CPU/memory requests & limits).
- [service.yml](service.yml) — `ClusterIP` Service mapping port `80` → `3213`.
- [ingress.yml](ingress.yml) — Traefik Ingress claiming path `/notes`.

## Project layout

```
notes/
├── Dockerfile
├── pom.xml
├── mvnw / mvnw.cmd
├── deployment.yml / service.yml / ingress.yml      # templated k8s manifests
├── ci/
│   ├── deploy.sh                                    # render + ship + invoke remote
│   ├── deploy-remote.sh                             # applies manifests in-cluster
│   └── nginx/notes.location.conf                    # host nginx /notes snippet
└── src/main/
    ├── resources/application.yml                    # config (env-var placeholders)
    └── java/com/application/notes/
        ├── NotesApplication.java                    # Spring Boot entry point
        ├── controller/NotesController.java          # REST API (/api/notes)
        ├── service/                                 # business logic + Kafka producers/consumers
        │   ├── NotesService(Impl).java
        │   ├── TranscriptionService(Impl).java
        │   ├── NoteEventProducer.java
        │   ├── SummaryReadyProducer.java
        │   ├── NoteIndexerConsumer.java
        │   ├── SummaryRequestConsumer.java
        │   └── SummaryEventConsumer.java
        ├── feignService/                            # AuthenticationClient (Feign), AiClient (RestTemplate)
        ├── repository/NotesRepository.java          # Spring Data JPA
        ├── model/                                   # Notes (entity), Users (DTO)
        ├── dtos/                                    # request/response/error DTOs
        ├── event/                                   # NoteEvent, SummaryRequestedEvent, SummaryReadyEvent
        ├── configuration/                           # interceptor, CORS, Kafka, Swagger
        ├── exceptions/                              # custom exceptions + GlobalExceptionHandler
        └── Utils/                                   # ApiResponse, Constants
```
