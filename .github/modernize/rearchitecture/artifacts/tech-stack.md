# Tech Stack

## Backend

- Language/runtime: Java 21 (`pom.xml` `java.version`; README states Java 21).
- Build: Maven, Spring Boot parent `3.4.5`, Spring Boot Maven plugin.
- Web/API: Spring MVC, Spring Validation, Spring Security OAuth2 resource server, Spring Actuator.
- Data: Spring Data JPA, PostgreSQL JDBC driver; PostgreSQL 16 with pgvector in Compose.
- AI: Spring AI BOM `1.0.0`; OpenAI and Ollama model starters; PGVector and Chroma vector-store
  starters; PDFBox-backed Spring AI PDF reader, Tika reader, and Spring AI RAG.
- Observability: Micrometer Prometheus registry.
- Other runtime dependency: Lombok (optional; excluded from packaged executable).
- Test dependencies: Spring Boot Test, Spring Security Test, Spring Boot Testcontainers,
  Testcontainers JUnit Jupiter, and Testcontainers PostgreSQL.
- Declared Maven dependencies (the individual dependency versions are managed, not overridden, by
  Spring Boot `3.4.5` and Spring AI BOM `1.0.0`):
  - `org.springframework.boot:spring-boot-starter-web`
  - `org.springframework.boot:spring-boot-starter-validation`
  - `org.springframework.boot:spring-boot-starter-data-jpa`
  - `org.springframework.boot:spring-boot-starter-security`
  - `org.springframework.boot:spring-boot-starter-oauth2-resource-server`
  - `org.springframework.boot:spring-boot-starter-actuator`
  - `io.micrometer:micrometer-registry-prometheus`
  - `org.postgresql:postgresql`
  - `org.projectlombok:lombok` (optional; omitted from the packaged executable)
  - `org.springframework.ai:spring-ai-starter-model-openai`
  - `org.springframework.ai:spring-ai-starter-model-ollama`
  - `org.springframework.ai:spring-ai-starter-vector-store-pgvector`
  - `org.springframework.ai:spring-ai-starter-vector-store-chroma`
  - `org.springframework.ai:spring-ai-pdf-document-reader`
  - `org.springframework.ai:spring-ai-tika-document-reader`
  - `org.springframework.ai:spring-ai-rag`
  - Test scope: `org.springframework.boot:spring-boot-starter-test`,
    `org.springframework.security:spring-security-test`,
    `org.springframework.boot:spring-boot-testcontainers`,
    `org.testcontainers:junit-jupiter`, and `org.testcontainers:postgresql`.
- Provider profiles: `openai,pgvector` default; alternatives `ollama,pgvector`, `openai,chroma`,
  and `ollama,chroma`.
- Authentication: stateless bearer JWT validation using HS256; no login or token-issuance endpoint.

## Frontend

- Language: JavaScript ES modules with JSX.
- UI: React and React DOM `^19.1.0` (lockfile resolution `19.3.1`).
- 3D rendering: Three.js `^0.176.0`, direct WebGL renderer usage.
- Build/dev server: Vite `^6.3.5` (lockfile resolution `6.4.6`) and `@vitejs/plugin-react`
  `^4.7.0` (lockfile resolution `4.7.0`).
- Runtime requirement: Node.js supporting the Vite version (the project environment was Node
  `22.14.0`).
- Backend integration: browser `fetch`, bearer Authorization header, multipart upload, and
  POST-based Server-Sent Events stream parsing.
- Local development proxy: `/api` and `/actuator` to `http://localhost:8080`.

## Relevant runtime configuration

Backend properties are in `src/main/resources/application.yml` and `.env.example`: database
connection, `JWT_SECRET`, provider API key/profile, model settings, vector store, file-size ceiling,
chunking, RAG limits, and query rewriting. The frontend token is entered at runtime and held in
component memory only; `VITE_API_BASE_URL` is optional and empty by default.
