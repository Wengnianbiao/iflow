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

Package root: `com.helianhealth.iflow.core`.

## Conductor Alignment

When changing decide/execute semantics, task lifecycle, or system-task contracts, use the local synchronous iFlow model as the product requirement. `conductor-3.30.2` remains useful as an algorithm reference, but its queue/worker/recovery lifecycle is intentionally not copied.

## Extension Boundaries

- **Built-in control-flow / synchronous system tasks** (INLINE, SWITCH, FORK, JOIN, TERMINATE, DO_WHILE, NOOP, …) live in this module.
- **Protocol / I/O system tasks** (HTTP, JDBC, WebService, ParamMap, SPI, …) live in sibling `iflow-*-task` modules. They depend on `iflow-core` and register as `WorkflowSystemTask` beans; do not drag protocol clients into core.
- DAO **interfaces** stay in `iflow-core` (`dao/`). Concrete persistence belongs in `iflow-repository` (or in-memory test doubles under `src/test`).
- Metadata DTOs shared with API/UI stay in `iflow-common` when they are not execution-runtime state.

## Coding Conventions

- In drive and scheduling code, prefer explicit loops and named boolean results. Do not use boolean `|=` or indirect persistence through method references / `Consumer` callbacks.
- Follow existing package layout (`execution/`, `execution/tasks/`, `execution/mapper/`, `service/`, `dao/`, `queue/`, `lock/`, `model/`).
- New system tasks: extend `WorkflowSystemTask`, implement `getTaskType()`, and ensure Spring registration into `SystemTaskRegistry`. One-shot business tasks implement `start()` and must reach a terminal status before returning; repeatedly evaluated control tasks implement boolean `execute()`. Do not add task-type `isAsync()` flags.
- Task `outputData` and workflow `output` are `Object`: preserve JSON-compatible objects, arrays, scalars, and null without adding `result` or `error` wrappers. INLINE and SPI return their value directly. HTTP/JDBC/WebService/MAPPING and control tasks own their object schemas; control tasks use `outputAsMap()` only when their output is known to be an object. Execution diagnostics belong in `reasonForInCompletion` (public view: `reasonForIncompletion`), never in business output. Explicit workflow `outputParameters` still builds an object; otherwise preserve the final task output. See `docs/task-output.md` at the repo root.
- The workflow caller owns the only drive loop, Decider calls, runtime task graph and persistence writes. Linear business tasks and all control tasks run on this thread; only FORK branch business tasks use the bounded branch pool. A completed fast branch may schedule its successor while other branches are still running.
- Register new task instances once, deduplicated by reference name and retry count, as `SCHEDULED`. The drive loop and `WorkflowExecutorOps.addNextIterationTasks()` share `ExecutionDAOFacade.registerTasks()` for deduplication, sequence assignment, persistence and runtime registration; do not add another registration layer. Registration does not depend on a drive context or schedule execution. The scheduler starts only `SCHEDULED` candidates selected by Decider. Pool-queued tasks remain `SCHEDULED`; the actual start changes them to `IN_PROGRESS` and records `startTime`.
- FORK workers execute a task copy and publish `Started`/`Completed` events. The caller applies complete results before deciding again. The invocation-local drive context holds branch events, in-flight branch IDs, the wait signal, pending persistence changes and closed state. Add a branch ID before submission and remove it when consuming completion; the set prevents duplicate submissions while a task remains `SCHEDULED` in the pool queue and its size gives the outstanding branch count. Enqueue events before releasing the semaphore. Do not add a pending-start queue, a bound Executor proxy, a global per-workflow Future registry or worker-driven Decider calls.
- `execute()` returns true only for real progress in status, loop iteration or task graph. JOIN may be evaluated repeatedly without progress. DO_WHILE registers both its first and subsequent iterations by calling `addNextIterationTasks()` on the `WorkflowExecutor` passed to `execute()`. `WorkflowExecutorOps` generates the iteration tasks and calls `ExecutionDAOFacade.registerTasks()`; the next Decider pass selects them for scheduling. Keep this direct callback free of scheduling and bound Executor proxies. Do not inject `DeciderService` into DO_WHILE or use `@Lazy` to resolve a dependency cycle.
- When no progress is possible, the caller waits only while branch calls remain outstanding. Close the context on termination and discard late results; there is no detached background execution or RUNNING-snapshot resume entry.
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
