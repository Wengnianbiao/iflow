# iFlow Agent Instructions

Instructions for AI coding agents working on this repository.

## Implementation Principles

- **Keep the existing owner.** Extend the method or class that already owns the behavior. Prefer a local helper before a new class, interface, service, or execution layer. Sharing a check between two callers alone does not require a new abstraction.
- **Implement the current requirement.** New extension points, dependencies, configuration switches, compatibility overloads, and fallback paths need a concrete requirement or an existing supported caller/data format. Future flexibility alone is insufficient.
- **Follow the reference deliberately.** For Conductor-aligned changes, inspect the corresponding local Conductor implementation and the iFlow caller before editing. Preserve its responsibility boundaries and algorithm; apply iFlow's documented differences instead of importing unrelated upstream infrastructure.
- **Keep contracts simple.** Use `void` for operations whose contract is validation, mutation, logging, or throwing. Return a value when a caller needs a meaningful result; do not invent generic `updated` flags or outcome objects just to pass control through layers.
- **Keep control flow visible.** Prefer direct calls, explicit conditions, and named intermediate results in orchestration and persistence paths. Avoid callback plumbing or generic frameworks for a concrete sequence of operations.
- **Match the surrounding style.** Follow nearby code for naming, formatting, constructors, dependency injection, logging, and exception handling. Do not convert unrelated code to another style.
- **Use plain Java for data models.** Entities, runtime models, DTOs, and configuration holders use explicit constructors and accessors instead of Lombok. Do not add builders by default; use them only where a concrete construction path benefits from named fields, and preserve existing supported APIs.
- **Test the production contract.** Do not add a production interface, injected dependency, or alternate execution path solely to simplify a test. Use existing seams and observable behavior first; a new abstraction must also fit the production design.
- **Preserve module direction.** Core must not depend on starter/demo or protocol implementations. Put behavior and configuration in the module that owns them; starter wires those modules together.

## Project Overview

iFlow is a Spring Boot–based pluggable workflow engine (Conductor-inspired) with a React console for visual orchestration. It is integrated through a Spring Boot Starter and includes an optional local demo for debugging and demonstrations.

Stack: Java 21, Maven multi-module, Spring Boot 3.4.x, React + Vite + TypeScript (`iflow-ui`).

## Agent Doc Layout

| File | Scope |
|------|--------|
| `AGENTS.md` (this file) | Implementation principles, repo map, shared checks |
| `iflow-ui/AGENTS.md` | Frontend / Conductor UI parity |
| `iflow-core/AGENTS.md` | Workflow engine core |

Read the nearest module `AGENTS.md` before changing that module; do not assume a nested file was loaded automatically when starting at the repository root. Keep domain-specific rules there rather than duplicating them here. The user's current instructions take precedence over repository guidance.

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
iflow-demo                # Local debugging / demo app; opt in with Maven -Pdemo
iflow-ui                  # React console (Vite); not a Maven module
conductor-3.30.2/         # Upstream Conductor reference (UI + engine patterns)
```

Dependency sketch: `common` → `repository` / `core` → task modules & services → `starter`. The optional `iflow-demo` assembles the runtime modules and is excluded from the default Maven reactor.

## Change Workflow

1. Inspect the target code, its callers, applicable module instructions, and relevant tests. For parity work, also inspect the corresponding Conductor source. Read supporting docs for the behavior being changed, not every document in the repository.
2. For a change spanning responsibilities, briefly state the intended behavior and which existing components own it before editing. Work in coherent steps; do not add adjacent cleanup, new product semantics, or speculative infrastructure to the task.
3. Apply user corrections to the whole affected path: implementation, callers, tests, and docs. Remove the superseded approach instead of wrapping it in another layer or leaving both paths in place.
4. Review the final diff against the rules below and run checks appropriate to the affected behavior. Report the result, relevant verification, and any remaining limitation. Passing tests do not establish that responsibility boundaries or project style were preserved.

## Code Review Rules

- **Scope and abstraction:** Every new class, interface, dependency, configuration option, and public API must serve the requested behavior. Reuse the existing owner when it can express the change clearly; remove abstractions left over from an abandoned approach.
- **One owner per rule:** Check that configuration resolution, validation, policy decisions, and state transitions each have a clear owner. Reuse that behavior from other callers instead of maintaining separate variants. Core-specific ownership is defined in `iflow-core/AGENTS.md`.
- **Compatibility with evidence:** Preserve existing public contracts and supported persisted data. Do not introduce silent null/default handling, aliases, or compatibility branches for hypothetical callers; handle invalid data explicitly at the appropriate boundary.
- **Behavior and verification:** Review both the requested path and relevant failure/boundary cases. Tests should assert observable contracts; avoid tests that merely restate the implementation or justify an unnecessary layer.

## Common Commands

```bash
# Backend
mvn clean package -Dmaven.test.skip=true
mvn -pl iflow-core -am test
mvn -Pdemo -pl iflow-demo -am clean package -Dmaven.test.skip=true
java -jar iflow-demo/target/iflow.jar

# Frontend (iflow-ui)
cd iflow-ui && pnpm install
pnpm dev          # http://127.0.0.1:5174 (API proxy → IFLOW_API_TARGET or :8099)
pnpm exec tsc --noEmit
pnpm test:definition   # Workflow definition / validation changes
pnpm test:executions   # Execution search / task index changes
pnpm build
```

DB init scripts live under `docs/init_mysql/`. Product docs under `docs/`.
The demo source, configuration, and POM are version-controlled. Enable the Maven `demo` profile in IDEA and reload the project to run or debug its main class; this is independent of Spring profiles. Docker scripts are an explicit demo build entry point.
Run the relevant module tests for backend behavior changes. UI changes also follow the browser verification and checks in `iflow-ui/AGENTS.md`. Documentation-only changes need diff and link checks, not a full application build.

## Cross-Cutting Guardrails

- Do not remove existing user changes in a dirty worktree; keep diffs scoped to the request.
- Prefer matching existing module boundaries over inventing new layers.
- When behavior should align with Netflix Conductor, treat `conductor-3.30.2/` as the reference (engine under its Java modules; UI under `conductor-3.30.2/ui-next`).
- Keep visible product UI copy Chinese where the existing UI is Chinese; keep technical task-type labels (`INLINE`, `HTTP`, `SWITCH`, etc.) in English.
- Do not commit secrets, local stores (`.pnpm-store`, `.m2`), or build artifacts unless explicitly asked.
- Ask before creating git commits or PRs.

## Maintaining These Instructions

- Keep rules scoped, actionable, and durable: name the invariant and the preferred implementation path. Link detailed behavior docs or canonical source examples rather than copying them into every instruction file.
- Update guidance when the user requests it or a requested behavior change makes a rule stale. Do not change instructions merely to excuse an implementation that violates them. Consolidate recurring lessons; avoid appending every one-off fix as a new global prohibition.

## Where To Look Next

- UI / canvas / definition editor → `iflow-ui/AGENTS.md`
- Executor, Decider, system tasks, fork execution → `iflow-core/AGENTS.md`
- Synchronous execution behavior → `docs/synchronous-workflow-execution.md`
- Workflow timeout and restart cleanup → `docs/workflow-timeout.md`
- Runtime parameter resolution → `iflow-core/src/main/java/com/helianhealth/iflow/core/utils/ParametersUtils.java`
