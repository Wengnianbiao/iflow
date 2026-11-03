# 任务执行策略配置

任务策略直接定义在工作流的 `WorkflowTask` 上，随工作流版本管理，不再提供独立 `TaskDef` 注册。

失败重试已接入 Decider 和任务队列，支持立即重试及固定、线性、指数退避。首次启动延迟 `startDelay` 仍只支持编辑、保存和启动解析到实例快照，尚未接入首次执行调度。

不提供通用任务级超时。HTTP、WebService 的连接/读取超时、JDBC 的查询超时以及 GraalJS 求值时限由各自实现负责；SPI 扩展自行负责具体调用的超时。整个流程继续受[工作流整体超时](workflow-timeout.md)约束。

## 节点默认值

四项策略直接由 `WorkflowTask` 提供固定默认值，不从 `IflowProperties` 继承：

```json
{
  "retryCount": 0,
  "retryDelaySeconds": 0,
  "retryLogic": "FIXED",
  "startDelay": 0
}
```

创建节点、构建器和 JSON 读取采用同一默认语义；缺省或 null 均采用节点默认值，显式配置保留。

| 节点字段 | 含义 |
| --- | --- |
| `retryCount` | 首次执行之外允许的重试次数；0 表示不重试 |
| `retryDelaySeconds` | 重试基础等待间隔，单位秒；0 表示立即重试 |
| `retryLogic` | `FIXED`、`LINEAR_BACKOFF` 或 `EXPONENTIAL_BACKOFF` |
| `startDelay` | 首次启动延迟配置，单位秒；当前仅保存到快照 |

以基础间隔 d、第 n 次重试（从 1 开始）说明重试策略：固定间隔为 d，线性增长为 d × n，指数增长为 d × 2ⁿ⁻¹。基础间隔为 0 时三种方式均不等待，计算溢出时限制为 `Integer.MAX_VALUE` 秒。不提供额外倍率或随机抖动配置。

例如 HTTP 节点只配置 `retryCount: 2`，其余三项分别采用 `0`、`FIXED`、`0`，不受全局配置影响。

前端校验节点数值为 Java Integer 范围内的非负整数，并检查重试策略是否合法。后端使用普通 Jackson 反序列化，定义保存入口继续拒绝负数。

## 失败重试边界

重试资格按任务类型和失败状态判断，与 `WorkflowSystemTask.isAsync()` 或任务是否注册无关。`TaskType.isBuiltIn()` 为 true 的运行时控制任务一律不重试：`DECISION`、`SWITCH`、`FORK`、`JOIN`、`EXCLUSIVE_JOIN`、`DO_WHILE`。定义中的 `FORK_JOIN` 映射为运行时 `FORK`，属于同一控制任务。其余任务仅在失败状态可重试且次数未耗尽时参与重试。INLINE、MAPPING 的执行异常设置为 `FAILED_WITH_TERMINAL_ERROR`，通过状态排除重试。SPI 的调用超时仍由扩展负责，调用失败可进入重试判断。普通启动与同步等待不改变这些规则。

任务实现将调用错误和 I/O 超时转为失败，由 `DeciderService.retry()` 判断是否重试。每次重试生成新任务 ID，递增运行时 `retryCount`，通过 `retriedTaskId` 指向上一次尝试，并将旧尝试标记为 `retried`。旧尝试保留失败状态、诊断和输出；新尝试重置状态、输出、诊断及本次执行时间，保留首次调度时间、FORK 归属和循环轮次。输入保留已解析的补充字段，并按当前实例快照重新解析节点输入参数。

次数耗尽或状态不可重试时，`optional` 任务转为 `COMPLETED_WITH_ERRORS` 后继续；非 optional 的 `permissive` 任务保留失败状态并允许后继执行，JOIN 等其他分支全部结束后再处理失败；普通任务抛出携带失败尝试的 `TerminateWorkflowException`。普通失败对应工作流 `FAILED`，任务超时对应 `TIMED_OUT`，任务取消对应 `TERMINATED`。`optional` 和 `permissive` 不会跳过尚可执行的重试。

工作流准备完成时，Decider 按节点引用名选择重试次数最高的尝试，再检查非 optional 的 `permissive` 节点是否仍处于失败终态；有失败则汇总任务 ID、状态和原因，通过 `TerminateWorkflowException` 将工作流收尾为 `FAILED`。即使没有 JOIN，或已出现成功的 TERMINATE，也执行这项检查。重试已成功的旧失败记录不影响最终结果；`optional` 和 `permissive` 同时为 true 时，按 `optional` 处理。

`optional` 是控制台常用的“可选任务”配置。`permissive` 保留为默认 false 的节点字段，按需在节点 JSON 中设置 `"permissive": true`，不增加常用表单开关；它允许失败后继续执行，但不会让工作流忽略最终失败。

本次尝试的退避时间保存在运行记录的 `startDelayInSeconds` 中。正数间隔经 `ExecutionQueue` 的延迟消息唤醒，等待期间保持 `SCHEDULED`、`startTime=0`，不占用任务工作线程或实例锁。同步扩展任务的延迟重试也通过该队列在到期后执行；零间隔仍沿用原同步或异步调度路径。队列工作线程在开始业务调用前重新加载任务、工作流并复用 Decider 的工作流超时检查；工作流已终止或超时时跳过业务调用。取消、终止性错误和工作流整体超时不产生后续重试。

## 保存与实例快照

控制台通过“启动延迟”和“失败重试”开关展开对应参数。关闭启动延迟时恢复 `startDelay: 0`；关闭失败重试时恢复 `retryCount: 0`、`retryDelaySeconds: 0`、`retryLogic: FIXED`，并清理对应未完成草稿。已有非零启动延迟或重试次数自动显示为开启。开启后必填项未完成时阻止保存；`optional` 保留独立开关。保存、导入导出和普通参数编辑保留显式配置，后端读取并保存时写入节点默认值。

普通与同步启动都经过 `WorkflowExecutorOps.resolveWorkflowDefinition()`：只深拷贝一次完整定义，不再遍历节点从配置类补默认值；嵌套节点同样使用 `WorkflowTask` 自身的默认值。控制流节点的显式重试字段保留，但不会赋予它重试资格。`createWorkflowModel()` 使用这份独立定义，并继续负责工作流整体超时的默认值解析。原始元数据不被修改，后续定义修改不追溯已启动实例。

控制台的“执行策略”依次提供可选任务和启动延迟；非内置控制流节点显示“失败重试”，包括 INLINE、MAPPING 及未注册的自定义类型。表单按后端相同的任务类型分类，不再要求插件声明异步能力；能否实际重试仍取决于失败状态。

此前版本定义或快照中的节点 `timeoutSeconds`、`timeoutPolicy` 由后端读取时忽略，后端重新保存不再输出这两个字段；工作流顶层的同名字段保留。

定义和实例沿用现有 JSON 存储，无需数据库表结构迁移。运行记录中的 `taskDefName` 保留为节点任务名称，实际重试计数也保持原有字段，不再依赖独立任务注册定义。
