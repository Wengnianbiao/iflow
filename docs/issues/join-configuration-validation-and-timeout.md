# JOIN 配置校验与执行超时保护

本文档记录 JOIN 配置错误导致工作流无法结束的问题，以及工作流执行超时保护的优化需求。

## BUG：JOIN 引用不存在的分支任务时，工作流永久处于 RUNNING

### 基本信息

- 类型：配置校验缺陷
- 优先级：P1
- 影响范围：包含 `FORK_JOIN` 和 `JOIN` 的工作流

### 问题描述

`FORK_JOIN` 后的 `JOIN.joinOn` 可以配置任意任务引用。当引用已被删除、重命名，或不属于对应的并行分支时，系统仍允许保存并启动工作流。

本次异常实例中的 JOIN 配置为：

```json
{
  "joinOn": [
    "sync_pacs_ref",
    "mapping_m5c7s_ref"
  ]
}
```

实际分支任务为：

```text
sync_pacs_ref
sync_lis_ref
```

由于 `mapping_m5c7s_ref` 不存在，JOIN 永远无法满足“所有等待任务均进入终态”的条件。

### 实际结果

- `apply_join_ref` 一直保持 `IN_PROGRESS`。
- 工作流一直保持 `RUNNING`。
- 同步调用无法正常返回。
- 执行器可能持续空转，占用 CPU。
- 修改工作流定义后，已启动的异常实例仍无法自动恢复，因为运行实例保存的是启动时的定义快照。

### 根因

1. 后端保存工作流时只校验名称和版本，没有校验 `joinOn` 引用。
2. 前端 JOIN 表单允许手工输入任意任务引用。
3. 运行时遇到不存在的引用时只继续等待，没有失败保护。

相关代码：

- `iflow-repository/src/main/java/com/helianhealth/agent/mysql/dao/MySqlMetadataDAO.java`
- `iflow-core/src/main/java/com/helianhealth/agent/core/execution/tasks/Join.java`
- `iflow-ui/src/pages/definition/EditorPanel/TaskFormTab/forms/JoinTaskForm.tsx`

### 期望结果

工作流保存或发布时校验 JOIN 配置。引用不存在时拒绝保存，并明确指出 JOIN 节点和错误引用。

对于已经保存的历史错误定义，运行时必须快速失败，不能无限等待。

### 建议方案

1. 后端在新增、更新工作流定义时执行完整的引用关系校验。
2. `joinOn` 中的每个引用必须存在于对应的 `FORK_JOIN` 分支中。
3. 前端优先从对应分支末端任务中选择 `joinOn`，减少手工输入。
4. 删除、重命名分支任务或改变分支末端任务时，自动同步 JOIN 配置。
5. JOIN 运行时发现等待引用不存在时，立即将 JOIN 和工作流标记为失败。

建议错误信息：

```text
JOIN apply_join_ref 配置错误：等待任务 mapping_m5c7s_ref 不存在
```

### 验收标准

- [ ] `joinOn` 中的每个引用都必须属于对应的 `FORK_JOIN` 分支。
- [ ] 分支任务删除、重命名或末端任务变化时，JOIN 配置能够同步更新。
- [ ] 前端能够显示具体的无效引用，并阻止提交。
- [ ] 后端独立执行相同校验，不能依赖前端校验结果。
- [ ] 通过 API 提交错误配置时返回明确的参数错误。
- [ ] 历史错误定义运行时不会无限等待，而是进入明确的失败状态。
- [ ] 失败原因包含 JOIN 引用名和所有缺失的任务引用。
- [ ] 覆盖保存校验、更新校验和运行时防御的回归测试。

## 工作流超时与重启收尾实现

工作流超时字段、实例快照、Decider 检查与 sweeper 兜底已实现，默认期限为 **60 秒**。定义正数覆盖系统配置，缺省或 `0` 继承系统默认值；默认策略为 `TIME_OUT_WF`，也支持每次检查到超时时仅记录日志的 `ALERT_ONLY`。

sweeper 默认每秒检查运行实例，启动时将上次进程遗留实例收尾为 `INTERRUPTED`，取消其非终态任务，不恢复或重放执行。完整配置、状态和回调语义见 [工作流超时与进程重启收尾](../workflow-timeout.md)。

HTTP、JDBC、WebService 等任务各自的连接和读取超时仍由任务模块负责，未在本次变更中统一按工作流剩余预算缩短。
