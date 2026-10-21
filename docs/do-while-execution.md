# DO_WHILE：数组遍历与 JS 条件循环

DO_WHILE 共用 `loopOver` 循环体，提供两种互斥配置：顶层 `loopItems` 数组表达式，或 `loopCondition` JS 继续条件。必须且只能配置一个，选用字段不能留空。编辑器的“遍历数组 / JS 条件循环”选择不保存为额外字段。

DO_WHILE 在 `execute()` 中创建首轮及后续轮次，直接调用传入的 `workflowExecutor.addNextIterationTasks()`。`WorkflowExecutorOps` 生成带迭代引用名的循环体首任务，再调用 `ExecutionDAOFacade.registerTasks()` 完成去重、序号分配、持久化和运行时登记，将新任务置为 `SCHEDULED`。主驱动登记新任务时也使用这一公共登记方法。该回调不调用 `start()`，不提交线程池，也不需要绑定驱动上下文的 Executor 代理。

DO_WHILE 保持现有构造依赖，不注入 DeciderService，也不通过 `@Lazy` 处理循环依赖。任务使用生命周期参数中的 Executor 回调登记下一轮，Decider 的依赖留在 `WorkflowExecutorOps` 中。

下一轮 Decider 从工作流运行时选出这些 `SCHEDULED` 任务，再由调度器按执行域启动；后续任务继续由 Decider 按完成状态推进。主线程启动后将任务置为 `IN_PROGRESS`，FORK 业务任务排队时保持 `SCHEDULED`，通过在途分支 ID 集合防止重复提交。循环体不会因控制任务重复评估而重复启动。

DO_WHILE、FORK、JOIN 等控制节点始终由调用线程推进；只有 FORK 分支的业务任务在线程池中并发执行。调用线程消费分支完成事件后继续决策，快分支可先推进后继，无需等待同批慢分支。没有可推进的工作且仍有分支执行时，调用线程阻塞等待通知。循环首节点是 FORK_JOIN 时沿用这一规则，循环体及对应 JOIN 完成后才判断是否继续下一轮。`execute()` 只有在轮次、任务图或状态实际变化时返回 `true`，等待本轮任务时返回 `false`。完整协议见 [同步工作流执行模型](synchronous-workflow-execution.md)。

## 遍历数组

`loopItems` 只接受完整的 `${...}` 表达式，例如 `${workflow.input.orders}` 或 `${query.output.rows}`。表达式可带首尾空白；不接受 JSON 数组文本、拼接字符串或 JS 代码。

```json
{
  "name": "orders",
  "taskReferenceName": "orders",
  "type": "DO_WHILE",
  "loopItems": "${workflow.input.orders}",
  "loopOver": [
    {
      "name": "process_order",
      "taskReferenceName": "process_order",
      "type": "INLINE",
      "inputParameters": {
        "evaluatorType": "graaljs",
        "expression": "$.order",
        "order": "${orders.output.loopItem}"
      }
    }
  ]
}
```

进入循环时解析一次数组来源并深拷贝为固定快照。后续来源或元素被修改不会改变遍历范围。数组、集合统一为 List；`null`、对象、字符串或数字会使循环任务失败，首次循环体不会执行。空数组 `[]` 成功结束，执行零轮。数组中的 `null` 元素仍占一轮。

循环体使用 `${orders.output.loopItem}` 访问当前元素副本，使用 `${orders.output.loopIndex}` 获取从 0 开始的索引；`iteration` 从 1 开始。数组入口不运行 JS 条件，也不能与 `loopCondition` 同时配置。变量选择仅提示工作流输入及此前可用的输出。

快照保存在运行态 `inputData.loopItems`，随任务数据持久化；`inputParameters.loopItems` 是保留键，不能作为循环参数使用。

## JS 条件循环

JS 固定使用 GraalJS。先执行一轮循环体，再求值 `loopCondition`：返回 `true` 继续下一轮，返回 `false` 结束。条件为 `false` 仍执行一轮，条件留空不能保存或执行。返回值沿用现有布尔转换规则。

```json
{
  "name": "repeat",
  "taskReferenceName": "repeat",
  "type": "DO_WHILE",
  "loopCondition": "$.iteration < $.limit",
  "inputParameters": {
    "limit": "${workflow.input.limit}"
  },
  "loopOver": [
    {
      "name": "work",
      "taskReferenceName": "work",
      "type": "INLINE",
      "inputParameters": {
        "evaluatorType": "graaljs",
        "expression": "$.iteration",
        "iteration": "${repeat.output.iteration}"
      }
    }
  ]
}
```

JS 上下文包含循环参数、`iteration`、循环任务及本轮循环体输出、`workflow.input`。脚本参数在每轮结束后重新解析，可引用本轮循环体结果，例如 `${work.output}`。JS 条件使用 `$.字段`，编排入参表达式使用 `${...}`。

## 编辑、校验和保留记录

新建循环默认选择 JS，条件为 `false`。切换入口清除另一方案的字段，保留循环体和业务参数；数组入口初始为空，完成表达式前不能保存。源码同时配置两字段会报错，编辑器不会自动丢弃其中一项。缺省或 `null` 表示字段未配置。

定义注册、更新、保存及运行时使用相同配置校验，覆盖 SWITCH、FORK 和 DO_WHILE 中的嵌套任务；循环体必须至少包含一个任务。脚本异常、来源异常和循环体失败沿现有失败流程处理，诊断保存在任务失败原因中。

循环输出保留 `iteration` 和按轮次编号收集的循环体输出。数组遍历还包含 `loopItem`、`loopIndex`。`inputParameters.keepLastN` 继续控制最近 N 轮输出和任务记录的保留。

顶层字段已由 `items` 改名为 `loopItems`，不提供 `items` 或 `_items` 别名，也不自动迁移存量定义；业务入参和表达式路径中的 `items` 名称不受此重命名影响。运行态仍使用已有任务数据存储，无需数据库结构迁移。
