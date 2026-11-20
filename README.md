# DocuChat AI – Enterprise RAG Platform

Implementation of `SRS-RAG-SPRINGAI-2026-V2`: chat with your documents using Spring AI,
PGVector / ChromaDB, PDFBox + Tika readers and dense embeddings.

**Stack:** Java 21 · Spring Boot 3.4 · Spring AI 1.0 · PostgreSQL (pgvector) / ChromaDB · OpenAI or Ollama

## Run

```bash
cp .env.example .env            # set OPENAI_API_KEY and JWT_SECRET
docker compose up -d postgres   # PostgreSQL + pgvector
mvn spring-boot:run             # profiles: openai,pgvector (default)
```

| Goal                       | Profiles                                                              |
|----------------------------|-----------------------------------------------------------------------|
| Cloud + Postgres (default) | `openai,pgvector`                                                     |
| Cloud + Chroma             | `openai,chroma` (`docker compose --profile chroma up -d chroma`)      |
| Fully air-gapped           | `ollama,pgvector` (`ollama pull llama3.1 && ollama pull nomic-embed-text`) |

A Postgres instance is always required (document registry), even with Chroma.

## Auth

Register with `POST /api/v1/auth/register` using a valid email and a password of at least 12 characters.
Registration creates the account and returns a Bearer token; `POST /api/v1/auth/login` accepts the
same email/password pair. Tokens use HS256, contain the account UUID as `sub`, and expire after 15
minutes. Passwords are stored as BCrypt hashes. All document and chat endpoints still require a
valid Bearer token; the `sub` claim remains the tenant `user_id` (NFR-SEC-01).

Set `JWT_SECRET` to at least 32 bytes of random data before starting the backend. Generate one with
`node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"`. The server refuses to
start with an empty or too-short signing key.

## API

| Verb | Path | Notes |
|------|------|-------|
| POST | `/api/v1/auth/register` | public signup; email + password (min 12 characters); returns access token |
| POST | `/api/v1/auth/login` | public login; returns access token |
| POST | `/api/v1/documents/upload` | multipart `file`, `forceReindex`; 201 new, 200 deduplicated |
| GET | `/api/v1/documents` | caller's documents |
| DELETE | `/api/v1/documents/{id}` | removes vectors + registry row |
| POST | `/api/v1/chat` | JSON answer + `sources[]`, `usage`, `latencyMs` |
| POST | `/api/v1/chat/stream` | SSE: `token` events then one `metadata` event with citations |

Errors are RFC 7807 Problem Details with a `code` field (`ERR_EMPTY_FILE`, `ERR_FILE_TOO_LARGE`, ...).

## Frontend

The React/Vite client is in `frontend/`. It provides document upload and management, chat with
page-level citations, and a Three.js ambient scene.

```bash
cd frontend
npm install
npm run dev
npm run build
```

The dev server proxies `/api` to `http://localhost:8080`; start the backend separately. The frontend
currently accepts a bearer token in settings; account registration and login are available from the
backend API. For a separately hosted frontend, set `VITE_API_BASE_URL` to the backend origin and
configure the backend to allow that origin. The production bundle is written to `frontend/dist/`.

## Known deviations from the SRS

- `TokenTextSplitter` has no overlap setting, so the 150-token overlap in the architecture diagram is not implemented.
- Chat memory is in-memory; use the JDBC chat-memory repository for multi-instance deployments.
- Ingestion is synchronous. Workspace Admin shared collections (SRS 2.4) have no FR and are not implemented.
- `ERR_EMBEDDING_PROVIDER` vs `ERR_VECTOR_STORE_DOWN` mapping is heuristic for Chroma/HTTP failures.
- SRS section 7 (RAGAS-style evaluation, Testcontainers ITs) is not yet written.
