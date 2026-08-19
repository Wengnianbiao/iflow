# 同步工作流执行模型

iFlow 面向院内本地接口串联，工作流由入口请求同步驱动，不使用任务队列、后台 Worker 或任务恢复轮询。`WorkflowSystemTask` 只有一个 `execute` 生命周期：

- 普通链路的 INLINE、HTTP、JDBC、WebService 等任务由启动工作流的调用线程执行；
- 只有 FORK 分支执行域内的任务提交到专用有界线程池；
- `startWorkflow` 同步等待工作流终态后返回完整运行时结果；
- WAIT、`startDelay`、`asyncComplete` 以及队列拉取次数均不属于运行模型；
- 任务状态和工作流状态仍会持久化，用于查询、审计与问题定位，但不会据此恢复执行。

## FORK / JOIN

FORK Mapper 一次生成 FORK 标记、各分支首任务和 JOIN。分支任务携带运行时
`forkTaskId`，后继任务继承该执行域；对应 JOIN 恢复 FORK 控制节点原有执行域，
因此嵌套 FORK 在内部 JOIN 后仍会回到外层分支池。

`WorkflowExecutorOps` 在调用线程内串行运行 Decider。FORK 分支完成后只更新自身状态并发出进度信号，
调用线程被唤醒后重新决策；不保存任务或完成事件队列。JOIN 每轮只检查 `joinOn` 中的目标任务：
未全部进入终态时保持 `IN_PROGRESS`，调用线程等待分支进度而不空转；全部完成后 JOIN 及其后续普通链路
继续由原调用线程推进。普通失败立即阻断，permissive 失败等待全部目标终态后裁决，optional 失败汇总为
`COMPLETED_WITH_ERRORS`。循环体内的 JOIN 只等待当前 iteration 对应的任务。

返回“未推进”的 JOIN / DO_WHILE 会记录本次评估对应的工作流语义版本；只有其他任务真正改变状态后才允许
再次派发，避免嵌套控制节点在线程池中反复唤醒形成轮询。

Decider 的待派发结果允许同时包含新任务和需要再次评估的已有控制任务。Ops 仅对新任务执行创建持久化，
Dispatcher 则按 taskId 防止并发重复执行；创建去重与派发去重不能合并，否则等待中的 JOIN 无法被重新评估。

默认配置：

```yaml
iflow:
  workflow-execution:
    fork-thread-count: 32
    fork-queue-capacity: 256
```

旧的 `task-thread-count` / `task-queue-capacity` 仍作为兼容配置回退。分支池饱和时任务会明确失败，
不会退化成调用线程执行。Decider、任务登记和工作流状态转换在 workflow lock 内完成，具体任务执行始终在锁外。
HTTP、JDBC、WebService 等客户端仍应设置连接和读取超时，避免同步入口无限等待。
