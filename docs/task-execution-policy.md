# 任务执行策略配置

任务策略直接定义在工作流的 `WorkflowTask` 上，随工作流版本管理，不再提供独立 `TaskDef` 注册。

当前重试与启动延迟仅支持编辑、保存和启动解析到实例快照，尚未接入重试调度或延迟唤醒。已有 `optional` 仍按原有逻辑处理任务失败。

不提供通用任务级超时。HTTP、WebService 的连接/读取超时、JDBC 的查询超时以及 GraalJS 求值时限由各自实现负责；SPI 扩展自行负责具体调用的超时。整个流程继续受[工作流整体超时](workflow-timeout.md)约束。

## 节点配置与框架默认值

四项策略逐项解析：节点非 null 值优先；缺省或 null 继承 `IflowProperties.taskExecution`。框架默认配置如下：

```yaml
iflow:
  task-execution:
    default-retry-count: 0
    default-retry-delay-seconds: 0
    default-retry-logic: FIXED
    default-start-delay: 0
```

| 节点字段 | 含义 |
| --- | --- |
| `retryCount` | 首次执行之外允许的重试次数；0 表示不重试 |
| `retryDelaySeconds` | 重试基础等待间隔，单位秒；0 表示立即重试 |
| `retryLogic` | `FIXED`、`LINEAR_BACKOFF` 或 `EXPONENTIAL_BACKOFF` |
| `startDelay` | 节点满足调度条件后的启动延迟，单位秒；0 表示无延迟 |

以基础间隔 d、第 n 次重试（从 1 开始）说明重试方式：固定间隔为 d，线性增长为 d × n，指数增长为 d × 2ⁿ⁻¹。基础间隔为 0 时三种方式均不等待。本次不提供额外倍率或随机抖动配置。

例如 HTTP 节点只配置 `retryCount: 2` 和 `startDelay: 0`，这两个值直接保留，另外两项继承框架配置。即使框架设置了正数启动延迟，该节点仍使用 0。

节点数值必须为 Java Integer 范围内的非负整数。错误类型、整数溢出或非法重试方式不会被静默转换；负数在定义保存时拒绝。框架配置绑定同样拒绝非法值。

## 失败重试边界

重试资格按任务类型和失败状态判断，与 `WorkflowSystemTask.isAsync()` 或任务是否注册无关。`TaskType.isBuiltIn()` 为 true 的运行时控制任务一律不重试：`DECISION`、`SWITCH`、`FORK`、`JOIN`、`EXCLUSIVE_JOIN`、`DO_WHILE`。定义中的 `FORK_JOIN` 映射为运行时 `FORK`，属于同一控制任务。其余任务仅在失败状态可重试且次数未耗尽时参与重试。INLINE、MAPPING 的执行异常设置为 `FAILED_WITH_TERMINAL_ERROR`，通过状态排除重试。SPI 的调用超时仍由扩展负责，调用失败可进入重试判断。普通启动与同步等待不改变这些规则。

未来接入调度时，任务实现将调用错误和 I/O 超时转为失败，由 Decider 判断是否重试；次数耗尽后再按现有 `optional`、`permissive` 或普通失败路径收尾。取消、明确不可重试的终止错误，以及工作流整体超时后的任务均不继续重试。本次仅收敛配置，未实现这段调度逻辑。

## 保存与实例快照

控制台为启动延迟、重试次数、重试间隔和重试方式分别提供开关。关闭开关表示移除对应节点覆盖值并继承框架配置；开启后必须填写数值或选择重试方式，未填写时阻止保存。已有非 null 配置（包括显式 0）自动显示为开启。保存、导入导出和普通参数编辑保持未配置状态，避免将当前框架默认值固化进工作流定义。

普通与同步启动都经过 `WorkflowExecutorOps.resolveWorkflowDefinition()`：读取定义后先深拷贝一次，再通过 `collectTasks()` 遍历普通、分支和循环体节点，为所有节点补齐启动延迟，仅为非内置控制流节点补齐三项重试默认值。控制流节点已有的显式重试字段保留，但不会赋予它重试资格。`createWorkflowModel()` 使用这份独立定义，并继续负责工作流整体超时的默认值解析。原始元数据不被修改，后续定义或框架配置变化不追溯已启动实例。

控制台的“执行策略”包含启动延迟和可选任务；非内置控制流节点显示“失败重试”，包括 INLINE、MAPPING 及未注册的自定义类型。表单按后端相同的任务类型分类，不再要求插件声明异步能力；能否实际重试仍取决于失败状态。

此前版本定义或快照中的节点 `timeoutSeconds`、`timeoutPolicy` 由后端读取时忽略，后端重新保存不再输出这两个字段；工作流顶层的同名字段保留。

定义和实例沿用现有 JSON 存储，无需数据库表结构迁移。运行记录中的 `taskDefName` 保留为节点任务名称，实际重试计数也保持原有字段，不再依赖独立任务注册定义。
