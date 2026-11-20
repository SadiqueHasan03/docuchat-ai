# Project Structure

## Project classification

Full-stack application: a Spring Boot REST API with a React single-page frontend in `frontend/`.
The backend remains the system of record for document ingestion, tenant identity, retrieval, and
chat generation.

## Top-level modules

| Module | Responsibility |
|---|---|
| `src/main/java/com/docuchat` | Spring Boot API and RAG application |
| `src/main/resources` | Runtime configuration and database initialization schema |
| `src/test/java/com/docuchat` | Focused service tests |
| `frontend` | React/Vite workspace UI and Three.js ambient scene |
| `.github/workflows` | Maven CI workflow |
| `docker-compose.yml`, `Dockerfile` | Local service topology and backend image |

## Backend layers and source counts

Counts are direct from the current source inventory; generated `target/` files are excluded.

| Layer/package | Main source files | Responsibility |
|---|---:|---|
| Application root | 1 | Spring Boot entry point and configuration-property scanning |
| `config` | 4 | JWT security, current-user lookup, AI beans, typed properties |
| `web` controllers | 2 | Document and chat HTTP routes |
| `web.dto` | 5 | Request and response records |
| `service` | 6 | Ingestion, chat, document operations, sanitization, MIME validation |
| `domain` | 3 | Document registry entity, status enum, JPA repository |
| `exception` | 3 | Error taxonomy, API exception, RFC 7807 handler |
| `src/main/resources` | 2 | `application.yml`, `schema.sql` |
| `src/test/java` | 2 | `TextSanitizerTest`, `MimeTypeValidatorTest` |

The React frontend has one externally reachable page at `/`. `frontend/src/App.jsx` owns workspace
views, local chat/document selection state, and token entry. `frontend/src/api.js` is the HTTP/SSE
boundary; `frontend/src/ThreeScene.jsx` renders the decorative WebGL scene.

## Runtime entry points and routes

- `DocuChatApplication.main` starts the Spring Boot service.
- `/api/v1/documents`: upload, list, and delete routes.
- `/api/v1/chat`: JSON chat and POST-based SSE chat stream.
- `/actuator/health/**` and `/actuator/prometheus`: the only unauthenticated backend routes.
- `frontend/index.html` → `frontend/src/main.jsx` → `frontend/src/App.jsx`: Vite/React page.

## Functional areas

- Document ingestion and library: MIME verification, content-hash deduplication, text extraction,
  chunking, vector storage, listing, and deletion.
- Retrieval-augmented chat: tenant-filtered search, optional query rewrite, conversational memory,
  streaming answers, and page-level source citations.
- Security and runtime configuration: externally issued HS256 JWTs, user scoping, model and vector
  store profiles, and ingestion/RAG settings.
- Frontend workspace: bearer-token connection, document management, chat composition, citations,
  and reduced-motion-aware Three.js ambience.

## Configuration and build files

- `pom.xml`: Java/Spring dependency and Maven build configuration.
- `src/main/resources/application.yml`: API, database, security, AI, ingestion, RAG, and profile
  settings.
- `src/main/resources/schema.sql`: `document_registry` initialization.
- `frontend/package.json` and `frontend/package-lock.json`: frontend dependencies and npm scripts.
- `frontend/vite.config.js`: local development server and backend API proxy.
- `frontend/.env.example`: optional frontend API origin.
- `docker-compose.yml`: Postgres, optional Chroma/Ollama, and backend services.
- `Dockerfile`: Maven build followed by a Java runtime image.
- `.github/workflows/ci.yml`: Java 21 Maven verification.

The frontend is independently developed and proxied to the API in local development. The backend
does not serve its production bundle; a separately hosted frontend requires same-origin routing or
backend CORS configuration.
