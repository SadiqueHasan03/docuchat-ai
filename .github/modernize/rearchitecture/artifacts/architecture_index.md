# Architecture Implementation Guide

This index is not the full contract. Do not implement from this file alone; follow the artifact paths below.

## Global artifacts

- `unit_graph.yaml`
  - Who reads: all implementation workers.
  - Use for: runtime entry points, API boundaries, exported signatures, dependencies, and shared references.
  - How to consume: find your assigned unit; source anchors prove behavior exists but do not define rewrite scope.
- `migration_boundary.yaml`
  - Who reads: all implementation workers.
  - Use for: exact new-frontend scope and files that remain unchanged.
  - How to consume: implement only `must_rewrite`; leave `copy_as_is` files untouched.
- `wire_contracts.yaml`
  - Who reads: workers implementing API integration.
  - Use for: endpoint, DTO, authorization, error, and SSE shapes.
  - How to consume: filter `contracts` by the assigned unit.
- `shared_modules.yaml`
  - Who reads: workers touching the backend auth context.
  - Use for: shared JWT subject helper and the restriction against client-supplied identity.
  - How to consume: filter modules by `used_by_units`.
- `cross_unit_state.yaml`
  - Who reads: workers implementing frontend-to-backend authentication.
  - Use for: bearer-token propagation and tenant-scope invariants.
  - How to consume: filter `flows` where `writer.unit` or `reader.unit` matches the assigned unit.
- `seams.yaml`
  - Who reads: workers implementing frontend API calls.
  - Use for: unchanged API boundaries and concrete browser-to-REST/SSE mappings.
  - How to consume: filter seams where `cut_between` includes the assigned unit.
- `project-structure.md`, `tech-stack.md`, `data-model.md`
  - Who reads: all workers.
  - Use for: existing modules/layers, runtime/dependency facts, and persisted/data-transfer shape.

## Implementation Guide

### Unit: `workspace-page`

- External trigger: browser navigation to `/`.
- Must read:
  - `units/workspace-page/behavior.yaml` — page states, API interactions, and user-visible outcomes.
  - `units/workspace-page/bindings.yaml` — React entry point, Vite proxy, token, and API configuration.
  - `units/workspace-page/unit_decomposition.yaml` — advisory split candidates only; `commit: false`.
- Relevant global rows:
  - `wire_contracts.yaml` rows where `unit` is `documents-api` or `chat-api`.
  - `shared_modules.yaml` backend auth helper only as a constraint; do not duplicate or change it.
  - `cross_unit_state.yaml` flows where `writer.unit` is `workspace-page`.
  - `seams.yaml` rows where `cut_between.migrated_side` is `workspace-page`.
- Before DONE report: artifact paths read, preserved upload/chat/error/citation behaviors, unresolved API/deployment assumptions, and frontend build evidence.

### Unit: `documents-api`

- External trigger: authenticated document list, upload, or delete route.
- Must read:
  - `units/documents-api/behavior.yaml` — deduplication, tenant isolation, error behavior, and deletion ordering.
  - `units/documents-api/bindings.yaml` — Spring routes, JWT binding, and ingestion configuration.
  - `units/documents-api/unit_decomposition.yaml` — advisory split candidates only; `commit: false`.
- Relevant global rows:
  - `wire_contracts.yaml` where `unit == documents-api`.
  - `shared_modules.yaml` where `used_by_units` includes `documents-api`.
  - `cross_unit_state.yaml` where `reader.unit == documents-api`.
  - `seams.yaml` where `cut_between.frozen_side == documents-api`.
- Before DONE report: exact API contracts read, preservation evidence, and any server changes required for a separately hosted deployment.

### Unit: `chat-api`

- External trigger: authenticated JSON chat or POST SSE stream.
- Must read:
  - `units/chat-api/behavior.yaml` — tenant-filtered retrieval, conversation memory, token events, final citations, and errors.
  - `units/chat-api/bindings.yaml` — routes, validation, security, and RAG runtime configuration.
  - `units/chat-api/unit_decomposition.yaml` — advisory split candidates only; `commit: false`.
- Relevant global rows:
  - `wire_contracts.yaml` where `unit == chat-api`.
  - `shared_modules.yaml` where `used_by_units` includes `chat-api`.
  - `cross_unit_state.yaml` where `reader.unit == chat-api`.
  - `seams.yaml` where `cut_between.frozen_side == chat-api`.
- Before DONE report: exact stream shape read, preserved tenant/citation/error behavior, and build/runtime verification.

## Completion evidence

Implementation workers must report artifacts read, every applicable `must_preserve` behavior
implemented, unresolved or deferred contracts, and test/build/runtime evidence. Do not infer a login
provider or add browser-side signing: the backend only validates externally issued HS256 bearer
tokens and contains no token-issuance endpoint.
