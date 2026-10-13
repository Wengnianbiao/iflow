# iFlow Core Agent Instructions

Rules for `iflow-core`, the workflow runtime engine. See root `AGENTS.md` for repo-wide context.

## Module Role

`iflow-core` owns execution-time models and the decide/execute loop:

- Models: `WorkflowModel`, `TaskModel`
- Orchestration: `WorkflowExecutor`, `WorkflowExecutorOps`, `DeciderService`
- System tasks: `WorkflowSystemTask` + built-ins under `execution/tasks/`
- Registration: `SystemTaskRegistry`
- Persistence port: `ExecutionDAO` (interface in core; implementations elsewhere)
- Synchronous single-process workflow entry with caller-thread linear tasks and pooled FORK branches (see `docs/synchronous-workflow-execution.md`)
- Scripting: GraalJS evaluators under `execution/evaluators/` and `execution/script/`

Package root: `com.helianhealth.agent.core`.

## Conductor Alignment

When changing decide/execute semantics, task lifecycle, or system-task contracts, use the local synchronous iFlow model as the product requirement. `conductor-3.30.2` remains useful as an algorithm reference, but its queue/worker/recovery lifecycle is intentionally not copied.

## Extension Boundaries

- **Built-in control-flow / synchronous system tasks** (INLINE, SWITCH, FORK, JOIN, TERMINATE, DO_WHILE, NOOP, …) live in this module.
- **Protocol / I/O system tasks** (HTTP, JDBC, WebService, ParamMap, SPI, …) live in sibling `iflow-*-task` modules. They depend on `iflow-core` and register as `WorkflowSystemTask` beans; do not drag protocol clients into core.
- DAO **interfaces** stay in `iflow-core` (`dao/`). Concrete persistence belongs in `iflow-repository` (or in-memory test doubles under `src/test`).
- Metadata DTOs shared with API/UI stay in `iflow-common` when they are not execution-runtime state.

## Coding Conventions

- Follow existing package layout (`execution/`, `execution/tasks/`, `execution/mapper/`, `service/`, `dao/`, `queue/`, `lock/`, `model/`).
- New system tasks: extend `WorkflowSystemTask`, implement `getTaskType()` and `execute()`, and ensure Spring registration into `SystemTaskRegistry`.
- Task `outputData` and workflow `output` are `Object`: preserve JSON-compatible objects, arrays, scalars, and null without adding `result` or `error` wrappers. INLINE and SPI return their value directly. HTTP/JDBC/WebService/MAPPING and control tasks own their object schemas; control tasks use `outputAsMap()` only when their output is known to be an object. Execution diagnostics belong in `reasonForInCompletion` (public view: `reasonForIncompletion`), never in business output. Explicit workflow `outputParameters` still builds an object; otherwise preserve the final task output. See `docs/task-output.md` at the repo root.
- Linear tasks execute on the workflow caller thread; only FORK branch scopes use the bounded branch pool. The scheduler waits for each parallel batch before returning, and only the caller-owned loop runs the next Decider round.
- Prefer small, focused changes; keep executor/decider changes covered by tests under `src/test/java`.

## Commands

```bash
# From repo root
mvn -pl iflow-core -am test
mvn -pl iflow-core -Dtest=ClassNameTest test
```

## Guardrails

- Do not move protocol task implementations into `iflow-core`.
- Do not bypass `ExecutionDAO` for production persistence.
- Do not broaden module dependencies casually (core should stay free of web/JDBC drivers; those belong in task modules or starter/web).
- Keep changes scoped to the requested engine behavior unless the user asks for a wider refactor.
