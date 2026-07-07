# 同步工作流执行模型

iFlow 面向院内本地接口串联，工作流由入口请求同步驱动，不使用任务队列、后台 Worker 或任务恢复轮询。`WorkflowSystemTask` 只有一个 `execute` 生命周期：

- INLINE、HTTP、JDBC、WebService 等任务提交到共享的有界线程池执行；
- `startWorkflow` 同步等待工作流终态后返回完整运行时结果；
- WAIT、`startDelay`、`asyncComplete` 以及队列拉取次数均不属于运行模型；
- 任务状态和工作流状态仍会持久化，用于查询、审计与问题定位，但不会据此恢复执行。

## FORK / JOIN

FORK Mapper 一次生成 FORK 标记、各分支首任务和 JOIN。可运行任务提交到共享线程池，任务执行完成后获取工作流锁并回调一轮 Decider，推进各分支后继节点。

JOIN 每轮只检查 `joinOn` 中的目标任务：未全部进入终态时保持 `IN_PROGRESS` 且不触发空转；任一依赖任务完成后的 Decider 回调会重新评估 JOIN。普通失败立即阻断，permissive 失败等待全部目标终态后裁决，optional 失败汇总为 `COMPLETED_WITH_ERRORS`。循环体内的 JOIN 只等待当前 iteration 对应的任务。

默认配置：

```yaml
iflow:
  workflow-execution:
    task-thread-count: 32
    task-queue-capacity: 256
```

线程池饱和时任务会明确失败，不在 Decider 持锁期间退化成调用线程执行。HTTP、JDBC、WebService 等客户端仍应设置连接和读取超时，避免同步入口无限等待。
