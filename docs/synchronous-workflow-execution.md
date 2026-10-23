# 工作流编排与同步调用

iFlow 按 Conductor 的编排顺序执行工作流：Decider 决策，当前线程执行同步任务，异步任务进入 JVM 内存队列；异步任务完成后，由该工作线程直接执行下一轮决策。决策不需要线程池，也不进入消息队列。

## 两种调用入口

| 入口 | 行为 |
| --- | --- |
| `WorkflowExecutionService.startWorkflow()` | 在当前调用线程创建实例并完成首次决策，返回实例 ID，不等待异步任务 |
| `WorkflowExecutionService.startWorkflowSync()` | 自行创建实例并完成首次决策，等待工作流终态后返回最终实例结果 |

`WorkflowExecutor` 和 `WorkflowExecutionService` 都保留 `startWorkflow()`、`startWorkflowSync()` 两个入口，调用方不传 mode。引擎中的两个入口分别实现启动流程，不通过 mode 分发或相互调用；定义解析、模型构建、任务调度等底层能力继续复用。服务层分别委托对应方法。控制台 `/start` 使用普通启动并返回实例 ID；Open API、Jarvis 使用同步服务入口，返回最终业务输出。

普通启动按 Conductor 的顺序解析定义、确定实例 ID、构建运行时模型，再持有实例锁创建实例并执行首次决策，释放锁后返回 ID。纯同步工作流可在首次决策中完成；遇到异步任务时，首次决策登记任务并入队后返回，后续状态与输出通过实例查询获取。启动阶段的持久化或决策异常会记录日志，尝试删除未成功启动的实例并重新抛出原异常；清理异常单独记录，不覆盖原异常。业务任务失败仍由决策逻辑保存工作流失败状态。

同步入口独立解析定义、确定实例 ID 并构建运行时模型，先向 `WorkflowCompletionRegistry` 登记 `Semaphore(0)`，再持有实例锁创建实例和执行首次决策，释放锁后等待终态。正常完成、失败、主动终止和 sweeper 超时均在持久化终态后通知；完成早于等待也不会丢失通知。同步入口在收到通知后重新读取持久化终态，退出或启动异常时移除等待记录。保留原有协议契约：必选任务失败抛出 `TerminateWorkflowException`，TERMINATE 指定的终态直接返回。

同步只表示等待最终结果，不决定业务任务使用哪个线程。等待线程中断时恢复中断标志、结束等待并清理登记，不修改工作流状态；取消执行需要显式调用 `terminateWorkflow()`。等待本身不设置额外时限，工作流超时仍由既有 sweeper 处理。

## 工作流生命周期回调

`WorkflowStatusListener` 位于 `com.helianhealth.iflow.core.listener`，定义与 iFlow 当前生命周期对应的事件和默认空回调，监听器按需覆盖：

| 事件 | 回调 | 触发位置 |
| --- | --- | --- |
| `STARTED` | `onWorkflowStarted(workflow)` | 实例创建成功后、首次决策前，对应 `RUNNING` |
| `COMPLETED` | `onWorkflowCompleted(workflow)` | 成功终态保存后 |
| `FAILED` | `onWorkflowFailed(workflow)` | 失败终态保存后 |
| `TIMED_OUT` | `onWorkflowTimedOut(workflow)` | 超时终态保存后 |
| `TERMINATED` | `onWorkflowTerminated(workflow)` | 终止终态保存后 |

执行层统一通过 `WorkflowExecutorOps.updateWorkflow()` 保存工作流。正常完成、失败、TERMINATE、主动终止和 sweeper 超时均在终态保存成功后，依次分发对应回调；保存失败不触发回调，普通 `RUNNING` 更新不重复触发 `STARTED`。DAO 只负责存储，`decide()` 和异步 worker 读取到已有终态时不补发通知。

`WorkflowCompletionRegistry` 实现此监听接口，通过四种终态回调唤醒已登记实例，每个等待者只释放一次 Semaphore，重复回调不会累积许可。启动回调不会唤醒等待者。执行器只在同步入口使用 Registry 登记和清理等待者；普通启动不登记，纯同步工作流在首次决策完成后直接返回。

监听器由 Spring 按接口收集并注入，手动组装执行器时须将同一个 Registry 加入监听器列表。所有监听器均收到相应事件。回调在保存状态的当前线程执行，可能仍持有实例锁，因此实现只能做轻量通知，不修改工作流，不执行 I/O、等待或调度。每个监听器的运行时异常单独记录，其他监听器继续执行，不改变已持久化的执行结果，也不阻止首次决策。

## 决策与调度

`WorkflowExecutorOps.decide()` 在触发它的线程直接执行：

1. 调用 `DeciderService.decide()` 获取待调度、待更新任务与完成标记。
2. 流程完成时，先持久化最后任务的修改，再通过统一保存入口保存工作流终态并触发生命周期回调。
3. 使用 `dedupAndAddTasks()` 去重并加入新任务，将同一列表交给 `scheduleTask(workflow, tasks)` 持久化与调度：同步任务立即 `start()`，异步任务进入任务队列。
4. 对同步系统任务调用 `execute()`，推进 JOIN、DO_WHILE 等控制逻辑。
5. 保存变化；同步任务有进展则在当前线程继续决策，没有进展则返回。

没有决策线程池、决策消息队列或每次调用的驱动上下文。首次决策由启动调用线程执行；异步任务完成后的决策由完成该任务的 I/O 工作线程执行。决策不等待 I/O。多个分支同时完成时，各自取得同一实例锁并重新加载最新状态再决策，不丢弃拿不到锁的完成通知。

DO_WHILE 通过 `workflowExecutor.scheduleNextIteration()` 生成本轮入口任务，追加 `__迭代号` 并保留外层 FORK 归属，然后直接调用同一个 `scheduleTask()`：同步入口任务在当前线程立即执行，异步入口任务直接入队，不等待下一轮 Decider 才启动。调度后将入口任务加入工作流；入口完成后的后续节点仍由 Decider 推进，不提前创建整个顺序循环体。

## 任务生命周期

```java
void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor);
boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor);
void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor);
```

`WorkflowSystemTask.isAsync()` 默认 false。任务的同步或异步属性不受 FORK 执行域影响。

| 任务 | 执行方式 |
| --- | --- |
| HTTP、JDBC、SPI、WEBSERVICE | 异步工作线程调用 `start()` 完成业务调用 |
| MAPPING、INLINE、NOOP | 当前决策线程同步调用 `start()` |
| FORK、SWITCH、TERMINATE | 当前决策线程同步调用 `start()` |
| JOIN、EXCLUSIVE_JOIN、DO_WHILE | 当前决策线程通过 `execute()` 推进 |

业务任务的 `start()` 返回时必须进入终态；`execute()` 只有在状态、迭代或任务图发生实际推进时返回 true。

任务状态为 `SCHEDULED → IN_PROGRESS → 终态`。等待在队列中的任务保持 SCHEDULED、startTime=0，工作线程真正执行时才记录 IN_PROGRESS 和开始时间。

## 内存任务队列

`QueueDAO` 负责异步任务消息存储，默认实现是 `InMemoryQueueDAO`。队列名为任务类型，消息 ID 为任务 ID。引擎通过 `ExecutionQueue` 接口提交异步任务，`InMemoryExecutionQueue` 是默认的准入与消费实现，只依赖一个异步任务 Executor：

1. 使用 `pushIfNotExists()` 入队，同一任务在排队和未确认阶段保持唯一。
2. 新消息触发任务消费者，工作线程通过 `QueueDAO.pop()` 获取任务 ID，按 ID 加载执行状态。
3. 处理完后 `ack()`；消费者继续处理队列中可见的消息，直到队列为空。
4. 提交被拒绝时回滚该条消息准入，保存任务和工作流失败状态，不影响其他已接纳的消息。

队列模式通过 `iflow.queue.type` 选择，未配置时默认 `memory`。`InMemoryQueueConfiguration` 使用 `@ConditionalOnProperty(havingValue = "memory", matchIfMissing = true)` 按条件注册 `QueueDAO` 和 `ExecutionQueue` 的内存实现。配置其他类型后，内存装配不生效，由对应扩展模块提供接口实现。引擎始终依赖 `ExecutionQueue` 接口；消息存储后端由 `QueueDAO` 接口隔离。

线程池中的 Runnable 只负责唤醒消费者，不携带工作流和任务快照。消息的准入与 pop 互斥，避免尚未确认准入的消息抢占已有消息的消费者。当前运行时提交立即可见的任务消息，不提供额外的延迟消费定时器。

内存消息不在进程退出后保留，也不从数据库恢复。DAO 保存执行状态，pending 查询用于查询、统计和超时处理，不用于拉取任务执行。

## 异步完成与并发保护

`executeSystemTask()` 的顺序为：

1. 根据任务 ID 加载所属实例，取得实例锁并重新加载最新工作流与任务；已终止或已处理的任务跳过。
2. 保存任务 IN_PROGRESS 与开始时间，复制执行快照，释放实例锁。
3. 在工作线程执行 I/O；部分输出和提前设置的终态不会发布到持久化任务。
4. 再次取得实例锁并重新加载最新状态；工作流或任务已终止时丢弃迟到结果。
5. 保存完整输入补充、输出、诊断与时间，释放锁。
6. 同一工作线程取得实例锁、重新读取状态并直接决策，不转交另一线程池。

工作流决策、任务开始、结果提交、控制台终止和 sweeper 超时使用同一实例锁；I/O 与同步等待不持锁。快速分支完成后可推进其后继，无需等待其他分支。控制节点不占用 I/O 线程等待子任务，单线程任务池也能执行嵌套 FORK/JOIN。

输出保留对象、数组、标量和 null 的 JSON 语义。诊断写入 `reasonForInCompletion`，任务补充的实际请求输入也会保存。已经开始的外部 I/O 不保证被取消回调撤销。

## 线程池配置

```yaml
iflow:
  queue:
    type: memory  # 默认值，可省略
  workflow-execution:
    task-thread-count: 32
    task-queue-capacity: 256
```

只有异步 I/O 任务池。移除了 `workflow-thread-count`、`workflow-queue-capacity` 和 `workflowTaskExecutor` Bean。旧 `fork-thread-count`、`fork-queue-capacity` 仍保留为任务池配置的回退值。
