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

All endpoints except `/actuator/health` and `/actuator/prometheus` need a Bearer JWT signed HS256 with
`JWT_SECRET`; the `sub` claim is the tenant `user_id` (NFR-SEC-01). Swap in an OIDC issuer for production.

## API

| Verb | Path | Notes |
|------|------|-------|
| POST | `/api/v1/documents/upload` | multipart `file`, `forceReindex`; 201 new, 200 deduplicated |
| GET | `/api/v1/documents` | caller's documents |
| DELETE | `/api/v1/documents/{id}` | removes vectors + registry row |
| POST | `/api/v1/chat` | JSON answer + `sources[]`, `usage`, `latencyMs` |
| POST | `/api/v1/chat/stream` | SSE: `token` events then one `metadata` event with citations |

Errors are RFC 7807 Problem Details with a `code` field (`ERR_EMPTY_FILE`, `ERR_FILE_TOO_LARGE`, ...).

## Known deviations from the SRS

- `TokenTextSplitter` has no overlap setting, so the 150-token overlap in the architecture diagram is not implemented.
- Chat memory is in-memory; use the JDBC chat-memory repository for multi-instance deployments.
- Ingestion is synchronous. Workspace Admin shared collections (SRS 2.4) have no FR and are not implemented.
- `ERR_EMBEDDING_PROVIDER` vs `ERR_VECTOR_STORE_DOWN` mapping is heuristic for Chroma/HTTP failures.
- SRS section 7 (RAGAS-style evaluation, Testcontainers ITs) is not yet written.
