# iFlow Core Agent Instructions

Rules for `iflow-core`, the workflow runtime engine. See root `AGENTS.md` for repo-wide context.

## Runtime Ownership

`iflow-core` owns execution-time models and the decide/execute loop. Keep behavior with its existing owner:

| Owner | Responsibility |
|-------|----------------|
| `WorkflowModel`, `TaskModel` | Runtime instance state |
| `DeciderService` | Graph decisions, timeout checks and timeout policy |
| `WorkflowExecutor` / `WorkflowExecutorOps` | Instance creation and definition snapshots, scheduling, driving execution, saving workflow state |
| `WorkflowSweeper` | Timeout fallback and restart cleanup; never advance the graph |
| `WorkflowSystemTask`, `SystemTaskRegistry` | System-task execution and registration |
| `WorkflowStatusListener` | Lifecycle callbacks after successful persistence |
| `ExecutionDAO` | Persistence port; concrete workflow storage lives in repository |

Package root: `com.helianhealth.iflow.core`. Follow the existing layout: `config/`, `execution/`, `execution/tasks/`, `execution/mapper/`, `listener/`, `service/`, `dao/`, `queue/`, `lock/`, `model/`. GraalJS evaluators live under `execution/evaluators/` and `execution/script/`.

Use local `conductor-3.30.2` as the decide/execute algorithm reference. iFlow is single-process with optional synchronous waiting. Its `QueueDAO` follows Conductor's message model, but the default and only implementation is JVM-memory `InMemoryQueueDAO`: no Redis/database queue and no execution restoration after restart. Do not add a decision executor, decision message queue, or Scheduler/context event layer.

## Extension Boundaries

- **Built-in control-flow / synchronous system tasks** (INLINE, SWITCH, FORK, JOIN, TERMINATE, DO_WHILE, NOOP, …) live in this module.
- **Protocol / I/O system tasks** (HTTP, JDBC, WebService, ParamMap, SPI, …) live in sibling `iflow-*-task` modules. They depend on `iflow-core` and register as `WorkflowSystemTask` beans; do not drag protocol clients into core.
- DAO **interfaces** stay in `iflow-core` (`dao/`). Concrete workflow persistence belongs in `iflow-repository`. The default `QueueDAO` is in-memory `InMemoryQueueDAO` in core, alongside the in-memory execution lock.
- Engine scheduling depends on the `ExecutionQueue` interface, never on `InMemoryExecutionQueue`. Queue implementations are registered by configuration classes, not component annotations on concrete classes. `iflow.queue.type` selects the mode; missing or `memory` activates `InMemoryQueueConfiguration`, while other modes are supplied by extension configurations. `QueueDAO` owns message storage; `ExecutionQueue` owns admission and consumer dispatch.
- Metadata DTOs shared with API/UI stay in `iflow-common` when they are not execution-runtime state.

## Creation and Synchronous Waiting

- `startWorkflow()` resolves the definition and instance ID, builds the runtime model, persists it and performs the first decision on the caller, then returns the instance ID. Startup infrastructure errors trigger best-effort instance cleanup and rethrow the original error.
- `startWorkflowSync()` independently creates the instance, registers completion before persistence/decision, and waits for the terminal result after releasing the instance lock. `WorkflowExecutionService` delegates both entry points with the same return types. Keep the two start implementations separate; do not add a mode dispatcher.
- Only `startWorkflowSync()` waits for completion. Register its completion Semaphore before creating and deciding the instance; remove registration on return or interruption. Interruption stops waiting without cancelling the workflow; cancellation requires explicit termination.
- Instance locks cover decisions and state updates, never I/O or semaphore waits. Decision and I/O workers do not wait for workflow completion. See [synchronous execution](../docs/synchronous-workflow-execution.md).

## Decision and Scheduling

- Follow Conductor directly: `decide()` calls Decider, registers candidates, calls `scheduleTask()`, evaluates nonterminal synchronous system tasks, persists changes, and loops only while synchronous work progresses. Return when async work is outstanding; never wait on I/O or retain a per-invocation drive context.
- Normal decisions deduplicate and add candidates by reference name and retry count through `dedupAndAddTasks()`, then pass that one list to `scheduleTask(workflow, tasks)`. Scheduling assigns sequence numbers, persists new tasks once, starts synchronous tasks immediately, and queues asynchronous tasks. Do not split scheduling into separate creation and execution lists.
- DO_WHILE uses `scheduleNextIteration()` on the provided `WorkflowExecutor` to generate and schedule the iteration entry directly, then append it to the workflow. Decider schedules successors after the entry completes. Do not reintroduce register-only loop callbacks, inject Decider into DO_WHILE, or use `@Lazy` to hide a dependency cycle.
- System-task `execute()` returns true only for real status, iteration, or graph progress. JOIN may wait without progress. Keep this meaningful progress result distinct from a generic workflow-dirty flag.

## Queue and Async Workers

- In-memory queue admission deduplicates by task ID through `QueueDAO`. Workers consume IDs with pop and acknowledge after handling. Queued tasks keep `startTime=0`; actual worker start records `IN_PROGRESS` and `startTime`.
- A worker locks and loads current task state, persists `IN_PROGRESS`, then executes an isolated snapshot outside the lock. It locks and reloads again before persisting complete results. Never persist a stale worker workflow snapshot.
- Discard late results for terminal tasks/workflows. After saving a result, run a new workflow decision directly on the same worker.
- Every decision acquires the instance lock and reloads current state. Do not drop concurrent completion triggers.

## Timeouts and Interruption

- `WorkflowExecutorOps.resolveWorkflowDefinition()` copies the definition once, fills missing start delays for all nodes, and fills retry defaults only for nodes outside the built-in control-flow set (the `FORK_JOIN` definition maps to runtime `FORK`) from `IflowProperties.taskExecution`; explicit zero values override defaults. `createWorkflowModel()` uses that isolated definition and resolves the effective workflow timeout once; a positive definition timeout overrides the system default. Do not mutate shared metadata or resolve global defaults again during checks. See [task execution policy configuration](../docs/task-execution-policy.md).
- Do not add generic node timeout metadata. Protocol tasks own I/O timeouts, GraalJS owns script evaluation limits, and SPI extensions own their call timeouts. `TaskType.isBuiltIn()` control tasks never retry; other tasks qualify by their failure status and retry budget, independently of `isAsync()` or registry membership. INLINE and MAPPING report execution errors as `FAILED_WITH_TERMINAL_ERROR`. Retry decisions belong in Decider. Cancellation, terminal errors and workflow termination do not trigger retries.
- Timeout checks and policy handling belong in **void `DeciderService.checkWorkflowTimeout()`**. Use `System.currentTimeMillis()` and the timeout in the runtime definition. Sweeper calls that same method without advancing execution; do not extract a separate checker/policy service or add `Clock` injection to this path.
- Decider exits for a terminal workflow before checking timeout and before generating tasks. Count from `lastRetriedTime` when present, otherwise `startTime`; trigger at the deadline. `TIME_OUT_WF` throws `TerminateWorkflowException` with `TIMED_OUT`; `ALERT_ONLY` logs on each expired check without changing or saving instance state or emitting a terminal event.
- The default timeout is 60 seconds and the default policy is `TIME_OUT_WF`. Timeout configuration and legacy-instance rules are documented in [workflow timeout](../docs/workflow-timeout.md); keep Decider and sweeper behavior consistent.
- Manual interruption uses `TERMINATED` (terminal, unsuccessful), cancels nonterminal tasks, and wakes synchronous waiters. Process restart cleanup uses `INTERRUPTED`; it takes precedence over timeout and cancels unfinished tasks because memory queues cannot resume. There is no resumable pause state.

## Persistence and Lifecycle

- Keep `WorkflowStatusListener` in `listener/`, with default callbacks for `STARTED`, `COMPLETED`, `FAILED`, `TIMED_OUT`, `TERMINATED`, and `INTERRUPTED`. Notify `STARTED` after successful creation and before the first decision in both entry points; ordinary `RUNNING` updates do not emit it.
- Save runtime workflow updates through `WorkflowExecutorOps.updateWorkflow()`, including sweeper timeouts and restart interruptions. Dispatch the matching terminal callback **after a successful save**. Do not put lifecycle callbacks in DAO code or signal completion on terminal reads.
- Invoke listeners on the saving thread and isolate each listener's runtime exceptions. Callbacks may hold the instance lock and must remain lightweight and read-only: no I/O, waits, or scheduling.
- `WorkflowCompletionRegistry` is an idempotent listener for all five terminal events, including sweeper outcomes. The executor uses it directly only for registration in `startWorkflowSync()`. Manual assembly must include the same Registry in the listener list.

## System Tasks and Output

- Extend `WorkflowSystemTask` and register in `SystemTaskRegistry`. One-shot business tasks implement `start()` and must reach a terminal status before returning; repeatedly evaluated control tasks implement boolean `execute()`.
- `isAsync()` defaults false; HTTP/JDBC/SPI/WEBSERVICE override true. MAPPING remains synchronous, including inside FORK.
- Task `outputData` and workflow `output` are `Object`: preserve JSON-compatible objects, arrays, scalars, and null without adding `result` or `error` wrappers. INLINE and SPI return their value directly. HTTP/JDBC/WebService/MAPPING and control tasks own their object schemas; use `outputAsMap()` only when the output is known to be an object.
- Diagnostics belong in `reasonForInCompletion` (public view: `reasonForIncompletion`), never in business output. Explicit workflow `outputParameters` builds an object; otherwise preserve the final task output.

## Coding and Verification

- Simple runtime input holders follow Conductor's plain Java constructors and explicit getters/setters; do not add Lombok annotations or builders to them.
- In drive and scheduling code, prefer explicit loops and named boolean results. Do not use boolean `|=` or indirect persistence through method references / `Consumer` callbacks.
- Keep executor/decider behavior covered by focused tests under `src/test/java`. Review ownership, lock/reload boundaries, and persistence-before-notification in the final diff as well as checking test results.

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
