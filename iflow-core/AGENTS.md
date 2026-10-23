# iFlow Core Agent Instructions

Rules for `iflow-core`, the workflow runtime engine. See root `AGENTS.md` for repo-wide context.

## Module Role

`iflow-core` owns execution-time models and the decide/execute loop:

- Models: `WorkflowModel`, `TaskModel`
- Orchestration: `WorkflowExecutor`, `WorkflowExecutorOps`, `DeciderService`
- System tasks: `WorkflowSystemTask` + built-ins under `execution/tasks/`
- Registration: `SystemTaskRegistry`
- Lifecycle callbacks: `WorkflowStatusListener` under `listener/`
- Persistence port: `ExecutionDAO` (interface in core; implementations elsewhere)
- Single-process execution with an optional synchronous wait. `QueueDAO` is the asynchronous task message queue; the default and only implementation is in-memory `InMemoryQueueDAO` (see `docs/synchronous-workflow-execution.md`)
- Scripting: GraalJS evaluators under `execution/evaluators/` and `execution/script/`

Package root: `com.helianhealth.iflow.core`.

## Conductor Alignment

Use `conductor-3.30.2` as the decide/execute algorithm reference. iFlow uses the same `QueueDAO` message model, but only the JVM memory implementation: no Redis or database queue, and no restore after process restart. `WorkflowExecutor.startWorkflow()` follows Conductor: resolve the definition, determine the instance ID, build the runtime model, persist and perform the first decision on the caller, then return the instance ID. Startup infrastructure errors trigger best-effort instance cleanup and rethrow the original error. `WorkflowExecutor.startWorkflowSync()` independently creates the instance, registers completion before persistence/decision, and waits for the terminal workflow result after releasing the instance lock. WorkflowExecutionService delegates both entry points with the same return types. Keep the two start implementations separate; do not add a mode dispatcher. Do not add a decision executor or decision message queue.

## Extension Boundaries

- **Built-in control-flow / synchronous system tasks** (INLINE, SWITCH, FORK, JOIN, TERMINATE, DO_WHILE, NOOP, …) live in this module.
- **Protocol / I/O system tasks** (HTTP, JDBC, WebService, ParamMap, SPI, …) live in sibling `iflow-*-task` modules. They depend on `iflow-core` and register as `WorkflowSystemTask` beans; do not drag protocol clients into core.
- DAO **interfaces** stay in `iflow-core` (`dao/`). Concrete workflow persistence belongs in `iflow-repository`. The default `QueueDAO` is in-memory `InMemoryQueueDAO` in core, alongside the in-memory execution lock.
- Engine scheduling depends on the `ExecutionQueue` interface, never on `InMemoryExecutionQueue`. Queue implementations are registered by configuration classes, not component annotations on concrete classes. `iflow.queue.type` selects the mode; missing or `memory` activates `InMemoryQueueConfiguration`, while other modes are supplied by extension configurations. `QueueDAO` owns message storage; `ExecutionQueue` owns admission and consumer dispatch.
- Metadata DTOs shared with API/UI stay in `iflow-common` when they are not execution-runtime state.

## Coding Conventions

- Simple runtime input holders should follow Conductor's plain Java constructors and explicit getters/setters; do not add Lombok annotations or builders.
- In drive and scheduling code, prefer explicit loops and named boolean results. Do not use boolean `|=` or indirect persistence through method references / `Consumer` callbacks.
- Follow existing package layout (`execution/`, `execution/tasks/`, `execution/mapper/`, `listener/`, `service/`, `dao/`, `queue/`, `lock/`, `model/`).
- New system tasks: extend `WorkflowSystemTask` and register in `SystemTaskRegistry`. One-shot business tasks implement `start()` and must reach a terminal status before returning; repeatedly evaluated control tasks implement boolean `execute()`. `isAsync()` defaults false; HTTP/JDBC/SPI/WEBSERVICE override true. MAPPING remains synchronous, including inside FORK.
- Task `outputData` and workflow `output` are `Object`: preserve JSON-compatible objects, arrays, scalars, and null without adding `result` or `error` wrappers. INLINE and SPI return their value directly. HTTP/JDBC/WebService/MAPPING and control tasks own their object schemas; control tasks use `outputAsMap()` only when their output is known to be an object. Execution diagnostics belong in `reasonForInCompletion` (public view: `reasonForIncompletion`), never in business output. Explicit workflow `outputParameters` still builds an object; otherwise preserve the final task output. See `docs/task-output.md` at the repo root.
- Follow Conductor directly: `decide()` calls Decider, registers candidates, calls `scheduleTask()`, evaluates nonterminal synchronous system tasks, persists changes and loops only while synchronous work progresses. It returns when async work is outstanding; it never waits on I/O or retains a per-invocation drive context. Do not reintroduce a Scheduler/context event layer.
- In normal decisions, deduplicate and add candidates by reference name and retry count through `dedupAndAddTasks()`, then pass that one list to `scheduleTask(workflow, tasks)`. Scheduling assigns sequence numbers, persists new tasks once, starts synchronous tasks immediately and queues asynchronous tasks. DO_WHILE uses `scheduleNextIteration()` to generate and schedule the iteration entry directly, then append it to the workflow; Decider schedules successors after the entry completes. Do not split scheduling into separate creation and execution lists or reintroduce register-only loop callbacks. In-memory task queue admission deduplicates by task ID through QueueDAO; workers consume IDs with pop and acknowledge after handling. Queued tasks keep startTime=0; actual worker start records IN_PROGRESS/startTime.
- Async workers lock/load current task state, persist IN_PROGRESS, execute an isolated snapshot outside the lock, then lock/reload and persist complete results. Discard late results for terminal tasks/workflows. After saving, run a new workflow decision directly on the same worker. Acquire the instance lock and reload current state for every decision; do not drop concurrent completion triggers. Never persist a stale worker workflow snapshot.
- `execute()` returns true only for real status, iteration or graph progress. JOIN may wait without progress. DO_WHILE schedules iteration entries by calling `scheduleNextIteration()` on the provided WorkflowExecutor; do not inject Decider into DO_WHILE or use `@Lazy` for a cycle.
- Only startWorkflowSync waits: determine the instance ID, register a completion Semaphore before creating and deciding the instance, signal after normal completion/failure/termination/sweeper terminal persistence, and remove it on return or interruption. Interruption stops waiting without cancelling the workflow; cancellation requires explicit termination. Decision and I/O workers do not wait for workflow completion. Instance locks cover each decision and state update, not I/O or semaphore waits. Memory queues do not resume after process restart.
- Keep `WorkflowStatusListener` in `listener/`, with default callbacks for STARTED, COMPLETED, FAILED, TIMED_OUT and TERMINATED. Notify STARTED after successful creation and before the first decision in both entry points; normal RUNNING updates do not emit it. Persist runtime workflow updates through `WorkflowExecutorOps.updateWorkflow()`, including sweeper timeouts, and dispatch the matching terminal callback only after a successful save. Invoke listeners on the saving thread and isolate each listener's runtime exceptions. `WorkflowCompletionRegistry` is an idempotent listener for all four terminal events; the executor uses it directly only for registration in `startWorkflowSync()`. Do not signal on terminal reads or put lifecycle callbacks in DAO code. Callbacks may hold the instance lock and must remain lightweight and read-only: no I/O, waits or scheduling. Manual assembly must include the same completion Registry in the listener list.
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
