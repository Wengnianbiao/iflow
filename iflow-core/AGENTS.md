# iFlow Core Agent 指南

本文件规定工作流运行时引擎 `iflow-core` 的开发约束。仓库级说明见根目录的 `AGENTS.md`。

## 运行时职责归属

`iflow-core` 负责运行时模型和决策／执行循环。各项行为应保留在现有的职责归属组件中：

| 组件 | 职责 |
|-------|----------------|
| `WorkflowModel`, `TaskModel` | 运行时实例状态 |
| `DeciderService` | 执行图决策、超时检查和超时策略处理 |
| `WorkflowExecutor` / `WorkflowExecutorOps` | 实例创建和定义快照、任务调度、执行推进、工作流状态保存 |
| `WorkflowSweeper` | 超时兜底和重启清理；禁止推进执行图 |
| `WorkflowSystemTask`, `SystemTaskRegistry` | 系统任务执行和注册 |
| `WorkflowStatusListener` | 持久化成功后的生命周期回调 |
| `ExecutionDAO` | 持久化接口；工作流存储的具体实现位于 repository 模块 |

根包为 `com.helianhealth.iflow.core`。沿用现有目录布局：`config/`、`execution/`、`execution/tasks/`、`execution/mapper/`、`listener/`、`service/`、`dao/`、`queue/`、`lock/`、`model/`。GraalJS 求值器位于 `execution/evaluators/` 和 `execution/script/`。

决策／执行算法以本地 `conductor-3.30.2` 为参考。iFlow 采用单进程运行，可选择同步等待。`QueueDAO` 遵循 Conductor 的消息模型，但默认且唯一的实现是基于 JVM 内存的 `InMemoryQueueDAO`：不使用 Redis／数据库队列，重启后不恢复执行。禁止新增决策执行器、决策消息队列或 Scheduler／上下文事件层。

## 扩展边界

- **内置控制流／同步系统任务**（INLINE、SWITCH、FORK、JOIN、TERMINATE、DO_WHILE、NOOP 等）放在本模块。
- **协议／I/O 系统任务**（HTTP、JDBC、WebService、ParamMap、SPI 等）放在同级的 `iflow-*-task` 模块。它们依赖 `iflow-core`，并注册为 `WorkflowSystemTask` Bean；禁止将协议客户端引入 core。
- DAO **接口**保留在 `iflow-core` 的 `dao/` 下。工作流持久化的具体实现属于 `iflow-repository`。默认的 `QueueDAO` 实现是 core 中的内存队列 `InMemoryQueueDAO`，内存执行锁也位于 core。
- 引擎调度依赖 `ExecutionQueue` 接口，禁止依赖 `InMemoryExecutionQueue`。队列实现由配置类注册，不要在具体实现类上添加组件注解。通过 `iflow.queue.type` 选择模式；未配置或值为 `memory` 时启用 `InMemoryQueueConfiguration`，其他模式由扩展配置提供。`QueueDAO` 负责消息存储；`ExecutionQueue` 负责入队准入和消费者分派。
- 与 API／UI 共享的元数据 DTO，如果不属于执行时的运行状态，应保留在 `iflow-common`。

## 实例创建与同步等待

- `startWorkflow()` 解析定义和实例 ID，构建运行时模型，持久化实例，并在调用线程上执行首次决策，随后返回实例 ID。启动过程中发生基础设施错误时，应尽力清理实例，并重新抛出原始错误。
- `startWorkflowSync()` 独立创建实例，在持久化／决策前注册完成通知，并在释放实例锁后等待终态结果。`WorkflowExecutionService` 委托调用这两个入口，并保留各自的返回类型。两个启动实现保持独立，禁止添加模式分派器。
- 只有 `startWorkflowSync()` 等待完成。在创建实例并执行决策前，注册用于完成通知的 Semaphore；返回或被中断时移除注册。中断只停止等待，不取消工作流；取消需要显式终止工作流。
- 实例锁只覆盖决策和状态更新，禁止覆盖 I/O 或信号量等待。决策工作线程和 I/O 工作线程都不等待工作流完成。详见[同步执行](../docs/synchronous-workflow-execution.md)。

## 决策与调度

- 直接遵循 Conductor：`decide()` 调用 Decider，注册候选任务，调用 `scheduleTask()`，评估尚未终结的同步系统任务，持久化变更，并且只在同步工作取得进展时继续循环。有异步或延迟重试工作尚未完成时返回；禁止等待 I/O，也禁止保留每次调用专用的执行推进上下文。
- 常规决策通过 `dedupAndAddTasks()`，按引用名和重试次数对候选任务去重并添加，然后将这一份列表传给 `scheduleTask(workflow, tasks)`。调度负责分配序号，将新任务持久化一次，立即启动无需等待的同步任务，并将异步任务和延迟重试任务入队。禁止将调度拆成独立的创建列表和执行列表。
- DO_WHILE 通过传入的 `WorkflowExecutor` 调用 `scheduleNextIteration()`，直接生成并调度本轮迭代入口任务，随后将其追加到工作流。入口任务完成后，由 Decider 调度后继任务。禁止重新引入只注册任务的循环回调、向 DO_WHILE 注入 Decider，或通过 `@Lazy` 掩盖循环依赖。
- 系统任务的 `execute()` 只有在状态、迭代或执行图确实推进时才返回 true。JOIN 可以处于等待中而没有进展。必须区分这一表示实际进展的返回值与通用的工作流脏标记。

## 队列与异步工作线程

- 内存队列在入队时通过 `QueueDAO` 按任务 ID 去重。工作线程通过 pop 消费 ID，处理后确认消息。排队中的任务保持 `startTime=0`；工作线程实际开始执行时才记录 `IN_PROGRESS` 和 `startTime`。
- 工作线程先加锁并加载任务当前状态，持久化 `IN_PROGRESS`，再在锁外使用隔离快照执行任务。持久化完整结果前，必须再次加锁并重新加载状态。禁止持久化工作线程持有的过期工作流快照。
- 对已终结的任务／工作流，丢弃迟到的执行结果。保存结果后，直接在同一工作线程上发起新一轮工作流决策。
- 每次决策都必须获取实例锁并重新加载当前状态。禁止丢失并发完成事件触发的决策。

## 超时与中断

- `WorkflowExecutorOps.resolveWorkflowDefinition()` 只复制一次定义。节点策略默认值归属于 `WorkflowTask`：重试次数、重试间隔和启动延迟为 0，重试方式为 FIXED；不从配置类补齐。`createWorkflowModel()` 使用这份隔离定义，并且只解析一次工作流的有效超时时间；定义中大于零的超时时间覆盖系统默认值。禁止修改共享元数据，也禁止在检查时重新解析全局默认值。详见[任务执行策略配置](../docs/task-execution-policy.md)。
- 禁止添加通用的节点超时元数据。协议任务负责 I/O 超时，GraalJS 负责脚本求值时限，SPI 扩展负责自身调用超时。`TaskType.isBuiltIn()` 判定的控制任务永不重试；其他任务是否可重试，由失败状态和剩余重试次数决定，与 `isAsync()` 或是否已在注册表中注册无关。INLINE 和 MAPPING 将执行错误报告为 `FAILED_WITH_TERMINAL_ERROR`。重试决策属于 Decider。取消、终止性错误和工作流终止都不触发重试。
- 超时检查和策略处理归属于**返回 void 的 `DeciderService.checkWorkflowTimeout()`**。使用 `System.currentTimeMillis()` 和运行时定义中的超时时间。Sweeper 调用同一方法，但不推进执行；禁止抽取独立的检查器／策略服务，也禁止在这条路径中注入 `Clock`。
- `WorkflowExecutorOps` 在调用 Decider 前拦截已终结的工作流。存在 `lastRetriedTime` 时从该时间计时，否则从 `startTime` 计时；到达截止时间即触发超时。`TIME_OUT_WF` 抛出携带 `TIMED_OUT` 状态的 `TerminateWorkflowException`；`ALERT_ONLY` 在每次检查发现超时时记录日志，不修改或保存实例状态，也不发出终态事件。
- 默认超时时间为 60 秒，默认策略为 `TIME_OUT_WF`。超时配置和历史实例规则见[工作流超时](../docs/workflow-timeout.md)；保持 Decider 与 Sweeper 的行为一致。
- 手动中断使用 `TERMINATED`（终态、执行未成功），取消非终态任务，并唤醒同步等待者。进程重启清理使用 `INTERRUPTED`；其优先级高于超时，并且由于内存队列无法恢复，必须取消未完成任务。不提供可恢复的暂停状态。

## 持久化与生命周期

- `WorkflowStatusListener` 保留在 `listener/` 中，为 `STARTED`、`COMPLETED`、`FAILED`、`TIMED_OUT`、`TERMINATED` 和 `INTERRUPTED` 提供默认回调。两个启动入口都在创建成功后、首次决策前通知 `STARTED`；普通的 `RUNNING` 更新不触发该事件。
- 运行时工作流更新统一通过 `WorkflowExecutorOps.updateWorkflow()` 保存，包括 Sweeper 触发的超时和重启中断。**保存成功后**才分派对应的终态回调。禁止在 DAO 代码中放置生命周期回调，也禁止在读取终态时发送完成信号。
- 在执行保存的线程上调用监听器，并隔离每个监听器抛出的运行时异常。回调执行时可能持有实例锁，必须保持轻量、只读：禁止 I/O、等待或调度。
- `WorkflowCompletionRegistry` 是覆盖全部五种终态事件的幂等监听器，包括 Sweeper 产生的终态。执行器只在 `startWorkflowSync()` 注册完成通知时直接使用它。手动组装时，必须将同一个 Registry 加入监听器列表。

## 系统任务与输出

- 扩展 `WorkflowSystemTask` 并注册到 `SystemTaskRegistry`。一次性业务任务实现 `start()`，并且必须在返回前进入终态；需要反复评估的控制任务实现返回 boolean 的 `execute()`。
- `isAsync()` 默认返回 false；HTTP／JDBC／SPI／WEBSERVICE 覆盖为 true。MAPPING 保持同步执行，包括位于 FORK 内时。
- 任务的 `outputData` 和工作流的 `output` 类型为 `Object`：保留兼容 JSON 的对象、数组、标量和 null，禁止添加 `result` 或 `error` 包装。INLINE 和 SPI 直接返回其结果值。HTTP／JDBC／WebService／MAPPING 以及控制任务各自负责自身的对象结构；只有确定输出为对象时才使用 `outputAsMap()`。
- 诊断信息放入 `reasonForInCompletion`（对外视图字段为 `reasonForIncompletion`），禁止放入业务输出。显式配置工作流 `outputParameters` 时构建对象；否则保留最后一个任务的输出。

## 编码与验证

- 简单的运行时输入数据类遵循 Conductor 的普通 Java 写法，使用构造方法和显式 getter／setter；禁止为其添加 Lombok 注解或构建器。
- 执行推进和调度代码优先使用显式循环和具名的布尔结果。禁止使用布尔 `|=`，也禁止通过方法引用／`Consumer` 回调间接执行持久化。
- 在 `src/test/java` 下用针对性测试覆盖执行器／决策器行为。除检查测试结果外，还必须在最终差异中审查职责归属、加锁／重新加载边界，以及先持久化后通知的顺序。

## 常用命令

```bash
# 在仓库根目录执行
mvn -pl iflow-core -am test
mvn -pl iflow-core -Dtest=ClassNameTest test
```

## 必须遵守的边界

- 禁止将协议任务实现移入 `iflow-core`。
- 生产环境持久化禁止绕过 `ExecutionDAO`。
- 不要随意扩大模块依赖范围（core 不应引入 web／JDBC 驱动；这些依赖属于任务模块或 starter／web）。
- 除非用户要求更大范围的重构，否则变更范围只限于本次要求的引擎行为。
