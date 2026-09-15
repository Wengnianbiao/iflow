# iFlow Agent Instructions

Instructions for AI coding agents working on this repository.

## Project Overview

iFlow is a Spring Boot–based pluggable workflow engine (Conductor-inspired) with a React console for visual orchestration. It can run standalone or as a Spring Boot Starter.

Stack: Java 17, Maven multi-module, Spring Boot 3.4.x, React + Vite + TypeScript (`iflow-ui`).

## Agent Doc Layout

| File | Scope |
|------|--------|
| `AGENTS.md` (this file) | Repo map, shared commands, cross-cutting guardrails |
| `iflow-ui/AGENTS.md` | Frontend / Conductor UI parity |
| `iflow-core/AGENTS.md` | Workflow engine core |

Prefer the nearest module `AGENTS.md` for domain-specific work. Do not duplicate those details here.

## Module Map

```
iflow-common              # Shared utils, enums, exceptions, metadata DTOs
iflow-repository          # Persistence / DAO / domain storage
iflow-core                # Executor, Decider, synchronous system-task abstractions, locks
iflow-*-task              # Pluggable system tasks (HTTP, JDBC, WebService, ParamMap, SPI)
iflow-gateway             # Inbound protocols (Open API / Jarvis / HL7)
iflow-console             # Admin / console APIs
iflow-ai                  # AI / MCP helpers
iflow-spring-boot-starter # Auto-config and property binding
iflow-web                 # Runnable app entry
iflow-ui                  # React console (Vite); not a Maven module
conductor-3.30.2/         # Upstream Conductor reference (UI + engine patterns)
```

Dependency sketch: `common` → `repository` / `core` → task modules & services → `starter` → `web`.

## Common Commands

```bash
# Backend
mvn clean package -Dmaven.test.skip=true
mvn -pl iflow-core -am test
mvn -pl iflow-web -am spring-boot:run   # or run the packaged jar under iflow-web/target

# Frontend (iflow-ui)
cd iflow-ui && pnpm install
pnpm dev          # http://127.0.0.1:5174 (API proxy → IFLOW_API_TARGET or :8099)
pnpm exec tsc --noEmit
pnpm build
```

DB init scripts live under `docs/init_mysql/`. Product docs under `docs/`.

## Cross-Cutting Guardrails

- Do not remove existing user changes in a dirty worktree; keep diffs scoped to the request.
- Prefer matching existing module boundaries over inventing new layers.
- When behavior should align with Netflix Conductor, treat `conductor-3.30.2/` as the reference (engine under its Java modules; UI under `conductor-3.30.2/ui-next`).
- Keep visible product UI copy Chinese where the existing UI is Chinese; keep technical task-type labels (`INLINE`, `HTTP`, `SWITCH`, etc.) in English.
- Do not commit secrets, local stores (`.pnpm-store`, `.m2`), or build artifacts unless explicitly asked.
- Ask before creating git commits or PRs.

## Where To Look Next

- UI / canvas / definition editor → `iflow-ui/AGENTS.md`
- Executor, Decider, system tasks, fork execution → `iflow-core/AGENTS.md`
- Synchronous execution behavior → `docs/synchronous-workflow-execution.md`
- Orchestration-tier `${...}` vs task-local `$.` expressions → `docs/expression-tiers.md`
