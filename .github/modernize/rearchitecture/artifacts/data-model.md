# Data Model

## Relational entity

`document_registry` is initialized by `src/main/resources/schema.sql` and mapped by
`com.docuchat.domain.DocumentRegistry`. It has no declared JPA relationships.

| Field | Database column | Java type | Constraints / behavior |
|---|---|---|---|
| `documentId` | `document_id` | `UUID` | Primary key; schema default `gen_random_uuid()` |
| `userId` | `user_id` | `String` / `VARCHAR(128)` | Required tenant owner |
| `fileName` | `file_name` | `String` / `VARCHAR(512)` | Required |
| `contentType` | `content_type` | `String` / `VARCHAR(128)` | Required |
| `fileSize` | `file_size` | `long` / `BIGINT` | Required |
| `sha256Hash` | `sha256_hash` | `String` / `CHAR(64)` | Required; used for per-user deduplication |
| `chunkCount` | `chunk_count` | `Integer` / `INTEGER` | Defaults to zero |
| `pageCount` | `page_count` | `Integer` / `INTEGER` | Defaults to zero |
| `status` | `status` | `DocumentStatus` / `VARCHAR(32)` | Required; `PROCESSING`, `INDEXED`, or `FAILED` |
| `errorMessage` | `error_message` | `String` / `TEXT` | Nullable; ingestion failure detail |
| `uploadedAt` | `uploaded_at` | `OffsetDateTime` / `TIMESTAMPTZ` | Required, schema default current timestamp |

Indexes cover `user_id` and `(user_id, sha256_hash)`.

## Relationships and vector metadata

No ORM relationships are declared. Spring AI manages the vector-store schema separately. Ingested
chunks carry `document_id`, `file_name`, `user_id`, `page_number`, `chunk_index`, and `uploaded_at`
metadata. Chat responses return source references containing document ID, filename, page number,
chunk index, similarity score, and snippet.

## API DTOs

- `DocumentSummary`: document ID, filename, content type, size, page/chunk counts, status, error,
  upload timestamp.
- `UploadResponse`: document ID, filename, content type, size, page/chunk counts, provider,
  status, processing time, upload timestamp.
- `ChatRequest`: optional conversation ID, question, optional document IDs, top-K, and similarity
  threshold. `user_id` is intentionally absent; the server derives it from JWT `sub`.
- `ChatAnswer`: conversation ID, answer, source list, token usage, and latency.
- `DeleteResponse`: document ID and deletion flag.

## State and transaction notes

Upload persists a `PROCESSING` registry row, writes vectors, then marks the row `INDEXED`; failures
purge partial vectors when possible and persist `FAILED` status. Deletion removes vectors before
deleting the registry row, leaving the row available for retry if vector deletion fails.
Conversation memory is in-memory and namespaced by user ID plus conversation ID. The schema contains
no explicit transaction relationship to the vector store.
