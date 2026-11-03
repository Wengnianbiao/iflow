# 工作流超时与进程重启收尾

每个工作流默认有 60 秒执行期限，默认策略是 `TIME_OUT_WF`。框架配置如下：

```yaml
iflow:
  workflow-execution:
    default-timeout-seconds: 60
    sweeper:
      enabled: true
      interval-seconds: 1
```

默认超时和扫描间隔必须为正整数秒。`sweeper.enabled=false` 关闭启动清理和周期兜底；Decider 仍会在每轮决策前检查超时。

## 定义覆盖与实例快照

```json
{
  "route": "SyncPacsLisNewApply",
  "name": "同步 PACS/LIS 新申请",
  "version": 1,
  "timeoutSeconds": 300,
  "timeoutPolicy": "TIME_OUT_WF",
  "tasks": []
}
```

`timeoutSeconds` 为正数时覆盖系统默认值；缺省或 `0` 时继承系统配置。定义保存只接受非负整数秒。`timeoutPolicy` 缺省或为 null 时使用 `TIME_OUT_WF`，显式 `ALERT_ONLY` 保持原值。

普通启动与同步启动都复制完整定义，将实际生效的期限写入实例的定义快照；不修改共享元数据。后续系统配置或定义修改不会改变已启动实例的执行期限。超时检查只读取实例定义快照，不再解析系统默认值。

控制台工作流属性中的“执行策略”通过“工作流超时”开关配置秒数和策略。开启后显示两个必填项，未填写时保留草稿并阻止保存；关闭后恢复 `timeoutSeconds: 0` 和 `TIME_OUT_WF`，继承系统超时时间。已有非零时间或非默认策略的配置自动显示为开启，包含使用系统时间的 `ALERT_ONLY`。输入 `0` 仍表示继承时间；`0` 与 `TIME_OUT_WF` 的组合显示为关闭。界面不把系统默认秒数硬编码为定义覆盖值。

## 两种超时策略

超时检查与策略处理直接放在 `DeciderService.checkWorkflowTimeout()` 中，sweeper 复用这个方法。以 `lastRetriedTime`（存在时）或 `startTime` 为起点，耗时达到期限即触发策略。

| 策略 | 到期行为 |
| --- | --- |
| `TIME_OUT_WF` | 结束工作流，保存为 `TIMED_OUT`，唤醒同步等待者 |
| `ALERT_ONLY` | 每次检查到超时时记录日志，工作流继续执行，同步调用继续等待终态 |

仅告警适合需要发现慢流程、但业务允许继续完成的场景。仅记录日志，不修改实例状态或业务输出，不额外保存实例，也不触发终态回调。

已终态实例由 `WorkflowExecutorOps` 在调用 Decider 前拦截。人工中断通过 `terminateWorkflow()` 进入 `TERMINATED`：这是未成功的终态，取消所有非终态任务、唤醒同步等待者，后续决策不再调度任务，迟到的 I/O 结果丢弃。

## Sweeper 的两个职责

sweeper 在启动时同步扫描一次，之后默认每秒扫描全部 `RUNNING` 实例。在同一实例锁内重新加载后处理，拿不到锁时留待下一轮。

1. **清理进程遗留实例**：实例开始时间早于本次 JVM 启动时间时，优先进入 `INTERRUPTED`，原因为“进程重启，内存执行队列已丢失，无法继续执行”。不受期限和超时策略影响。取消所有非终态任务，不恢复执行或重放外部请求。
2. **兜底当前实例超时**：使用与 Decider 相同的期限及策略。仅告警时只记录日志，终止策略到期时进入 `TIMED_OUT`。

新实例从 JVM 启动时间起算，不会被启动清理误处理。清理或持久化失败的实例保留在 pending 集合中，后续扫描继续尝试。已保存终态的实例不会被重复收尾。

当前运行时按单进程设计，未引入多实例进程归属判定。实例执行锁使用默认 60s 的租约（`iflow.lock-lease-time`），持锁线程不返回时，租约到期允许 sweeper 取得锁并继续检查超时。锁租约与工作流超时分别计时，租约到期本身不改变实例状态。扫描间隔代表检查频率，实际处理时间还受实例锁和持久化耗时影响；租约不会强制中断原同步任务，也不提供对旧线程写入的隔离。详见[工作流执行锁](synchronous-workflow-execution.md#异步完成与并发保护)。

## 统一终态收尾

Decider 异常、超时扫描、重启清理和手动终止复用任务取消及工作流保存逻辑。

- 工作流超时时，原 `IN_PROGRESS` 任务记为 `TIMED_OUT`，其他非终态任务记为 `CANCELED`。
- 重启中断和手动终止时，所有非终态任务记为 `CANCELED`。
- 已完成任务保持原结果；结束的任务补齐原因、结束时间及执行标记。
- 工作流终态通过 `WorkflowExecutorOps.updateWorkflow()` 保存；保存成功后才分发生命周期回调并通知完成 Registry。
- 新增 `onWorkflowInterrupted()` 默认空回调；`INTERRUPTED` 是失败终态，在统计中计入失败。
- 异步 I/O 晚于终态返回时丢弃结果，不能覆盖已取消或超时的状态。取消回调不保证撤销已经发生的外部操作。

状态和定义沿用 JSON 持久化，不需要数据库表结构迁移。
