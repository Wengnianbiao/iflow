# 同步工作流执行模型

iFlow 的工作流调用是同步的：`startWorkflow()` 在调用线程中驱动工作流，直到成功、失败或终止后返回。FORK 分支并发执行业务任务，JOIN 后的主链路等待汇聚条件满足。线程池并发不会将工作流变成后台作业，也不提供持久化快照恢复运行的入口。

## 生命周期与线程归属

系统任务的接口保留 `workflow`、`task`、`executor` 三个上下文参数：

```java
void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor);
boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor);
void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor);
```

`start()` 默认不做事，`execute()` 默认返回 `false`。生命周期和运行线程分别由任务实现及调度器决定，任务接口没有 `isAsync()`。

| 任务 | 生命周期 | 执行线程 |
| --- | --- | --- |
| HTTP、JDBC、WEBSERVICE、MAPPING、SPI、INLINE | `start()` 完成一次业务调用并进入终态 | 主链路在调用线程，FORK 分支在线程池 |
| NOOP | `start()` 直接完成任务 | 主链路在调用线程，FORK 分支在线程池 |
| FORK、SWITCH、TERMINATE | `start()` 完成一次性控制动作 | 调用线程 |
| JOIN、EXCLUSIVE_JOIN | `execute()` 重复检查汇聚条件 | 调用线程 |
| DO_WHILE | `execute()` 创建首轮、检查本轮、创建后续轮次 | 调用线程 |

业务任务的 `start()` 返回时必须进入终态，否则记为任务契约失败。`execute()` 的 `true` 表示状态、循环轮次或任务图发生实际推进；条件未满足、重复评估或仅刷新时间不能算推进。任务失败由后续 Decider 决策处理，完成通知不区分业务成功或失败。

```text
主链路：A → FORK ────────────────── JOIN → D
               ├─ B1 → B2 ─────────┤
               └─ C1 → C2 ─────────┘
```

B1 完成后，调用线程可以提交 B2，即使 C1 仍在运行。分支中的 SWITCH、循环及嵌套 FORK/JOIN 仍由调用线程解析和推进，业务任务沿当前 FORK 执行域提交。控制任务不会占据分支工作线程等待其他分支，因此单线程分支池也能执行嵌套结构。

## 首次启动与任务状态

主驱动和循环登记回调共用 `ExecutionDAOFacade.registerTasks()`，统一按 `referenceTaskName + retryCount` 去重、分配序号、持久化并登记到工作流运行时。登记只创建 `SCHEDULED` 任务，不调用 `start()`，也不提交线程池。Decider 选出待调度任务后，调度器只启动其中处于 `SCHEDULED` 状态的任务。

```text
SCHEDULED → IN_PROGRESS → COMPLETED / FAILED / 其他终态
```

- `SCHEDULED` 表示已经登记但尚未实际开始，也包括线程池排队时间。Mapper 仅设置调度时间，实际开始时间仍为零。
- `IN_PROGRESS` 表示已经实际开始；控制任务还可能等待汇聚或循环体完成。
- 实际调用开始才设置 `startTime`。终态输出、失败原因及结束时间完成发布后，Decider 才会看见完成结果。

DO_WHILE 的首轮和后续轮次都由 `execute()` 调用传入的 `workflowExecutor.addNextIterationTasks()` 登记。`WorkflowExecutorOps` 实现该方法，生成带迭代引用名的循环体首任务，再通过 `ExecutionDAOFacade.registerTasks()` 持久化并加入工作流运行时。下一轮 Decider 选出这些 `SCHEDULED` 任务，交给调度器启动。回调直接使用现有 Executor，不需要绑定驱动上下文的代理；DO_WHILE 不注入 DeciderService，Bean 装配也不需要通过 `@Lazy` 解除循环引用。

主线程直接启动的任务立即进入 `IN_PROGRESS`。FORK 任务在线程池中排队时仍为 `SCHEDULED`，驱动上下文使用在途分支 ID 集合记录已提交任务，重复决策不会重复提交。开始时间只能表示实际开始，不能用于判断任务是否已经提交。

公开任务状态和内部状态均包含 `SCHEDULED`，DAO JSON 数据无需新增表字段。pending 查询及既有 `task_in_progress` 索引同时覆盖 `SCHEDULED`、`IN_PROGRESS`，用于查询和统计，不作为后台恢复执行队列。控制台显示为“等待执行”和“执行中”。

## 分支结果交接与通知

每次调用创建局部驱动上下文，保存线程安全事件队列、在途分支 ID 集合、初始许可为零的信号量、本轮待持久化变化及关闭状态。在途集合同时用于提交防重和统计未结束分支数量。上下文不负责生成或登记任务，也不保存首次启动队列；不维护全局工作流执行 Map 或每任务 Future 表。

分支提交前将任务 ID 加入在途集合，仅首次加入时才提交。工作线程使用任务副本执行 `start()`，不直接写入主线程持有的任务实例。实际开始时发布 `Started`，执行返回或异常时发布 `Completed`。线程池拒绝提交也形成完成事件，保证主线程能处理失败并结束等待。

每次通知严格执行“事件先入队，再释放信号量”。即使通知先于主线程等待，许可仍会保留，不依赖易丢失通知的裸 `notify()`。开始事件更新运行态及实际开始时间；完成事件携带完整任务结果并从在途集合移除任务 ID。主线程合并输入补充、任意 JSON 输出、诊断及时间字段后持久化，再进入下一轮 Decider。

输出保留对象、数组、标量和 null 的原始 JSON 语义。任务副本即使提前设置 `COMPLETED`，返回前的输出写入也不会被主线程提前读取。WEBSERVICE 等任务补充的实际请求输入也随结果一起合并。

## 调用线程的推进循环

1. 消费分支事件，发布完整任务状态和结果。
2. 调用 Decider，登记新的任务实例。
3. 调度 Decider 选出的 `SCHEDULED` 任务：主链路直接调用 `start()`，FORK 业务任务经在途集合防重后提交线程池。
4. 在调用线程评估尚未终态的控制任务，通过 `execute()` 返回值判断实际推进。
5. 持久化变化；有推进或待消费事件时继续循环。控制任务本轮登记的任务留给下一轮 Decider 选出并调度。
6. 没有推进且仍有分支调用时等待通知；没有分支也无法推进时报告失败，避免空转和永久等待。

只有调用线程修改流程任务图、调用 Decider 和发布持久化结果。创建至同步执行结束保留实例锁。工作流终态或驱动异常退出时关闭局部上下文，排队分支跳过业务调用，迟到结果不能覆盖既定终态。已经开始的外部 I/O 不因关闭上下文而保证被撤销。

## 扩展迁移

旧的单方法系统任务需要迁移并重新编译，不提供兼容桥。一次调用完成的业务实现迁入 `start()`，重复评估的控制实现保留在 boolean `execute()`；SPI 的 `remoteInvoke` 接口不变。外部状态查询继续通过查询接口，不通过 `decide(String)` 或 `decideWithLock(...)` 再次驱动实例。

本次模型不扩展后台超时、控制台强制终止和分布式恢复机制。需要这些能力时，必须另行明确同一实例的驱动所有权和外部状态写入协议。
