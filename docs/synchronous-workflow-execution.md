# 同步工作流执行模型

iFlow 面向院内本地接口串联，工作流由入口请求同步驱动，不使用任务队列、后台 Worker，也不靠 sweep 恢复执行。`WorkflowSystemTask` 只有一个 `execute` 生命周期：

- 普通链路的 INLINE、HTTP、JDBC、WebService 等任务由启动工作流的调用线程执行；
- 只有 FORK 分支执行域内的任务提交到专用有界线程池；
- `startWorkflow` 同步等待工作流终态后返回完整运行时结果；
- WAIT、`startDelay`、`asyncComplete` 以及队列拉取次数均不属于运行模型；
- 任务状态和工作流状态仍会持久化，用于查询、审计与问题定位，但不会据此恢复执行。
- 执行期限到期后，`WorkflowSweeper` 只把当前运行实例标为 `TIMED_OUT`，进行中的任务标为 `TIMED_OUT`，其余未完成任务标为 `CANCELED`，然后结束同步调用。它不会重新 decide，也不会继续调度后续任务。进程重启后仍留在 `workflow_pending` 且已经到期的实例同样只终止。

MySQL 执行记录的输入、输出各自按 JSON 序列化后的 1,000,000 字符上限截断，创建和更新使用相同规则。
截断只发生在仓储层的入库 DTO 上，不替换或修改运行时上下文的输入、输出及嵌套值；后续节点的表达式取值和
同步调用返回结果仍使用完整数据。历史查询读到的是截断后的审计记录，工作流定义快照不受此上限影响。

```yaml
iflow:
  workflow-execution:
    default-timeout-seconds: 3600
    sweeper:
      enabled: true
      interval-seconds: 30
```

定义上的 `timeoutSeconds <= 0` 继承 `default-timeout-seconds`。两者都 `<= 0` 时不启用超时终止。

## FORK / JOIN

FORK Mapper 一次生成 FORK 标记、各分支首任务和 JOIN。分支任务携带运行时
`forkTaskId`，后继任务继承该执行域；对应 JOIN 恢复 FORK 控制节点原有执行域，
因此嵌套 FORK 在内部 JOIN 后仍会回到外层分支池。

`WorkflowExecutorOps` 只在工作流调用线程内运行 Decider 和推进 DAG。Scheduler 执行 Decider 选出的
本轮任务：根执行域任务在调用线程串行执行，连续的 FORK 分支任务作为一个并行批次提交，并在全部结束后
返回 Ops。Ops 随后才进入下一轮 Decider，不由分支线程回调或唤醒决策。JOIN 每轮只检查 `joinOn`
中的目标任务：未全部进入终态时保持 `IN_PROGRESS`；全部完成后 JOIN 及其后续普通链路继续由原调用线程推进。
普通失败立即阻断，permissive 失败等待全部目标终态后裁决，optional 失败汇总为
`COMPLETED_WITH_ERRORS`。循环体内的 JOIN 只等待当前 iteration 对应的任务。

SystemTask 的 `execute` 直接修改 `TaskModel`，不返回用于触发下一轮决策的状态位。Scheduler 完成本轮任务后，
同步主循环会自然进入下一轮 Decider。

DO_WHILE 任务只更新自身迭代状态；循环体任务由 Scheduler 返回后的下一轮主线程 Decider 创建。

Decider 的待派发结果允许同时包含新任务和需要再次评估的已有控制任务。Ops 仅对新任务执行创建持久化，
Scheduler 则按 taskId 防止并发重复调度；创建去重与调度去重不能合并，否则等待中的 JOIN 无法被重新评估。

默认配置：

```yaml
iflow:
  workflow-execution:
    fork-thread-count: 32
    fork-queue-capacity: 256
```

旧的 `task-thread-count` / `task-queue-capacity` 仍作为兼容配置回退。分支池饱和时任务会明确失败，
不会退化成调用线程执行。Decider 在 workflow lock 内完成，具体任务执行始终在锁外；本轮任务结束后，
由工作流调用线程统一持久化执行结果并继续下一轮。
HTTP、JDBC、WebService 等客户端仍应设置连接和读取超时，避免同步入口无限等待。
