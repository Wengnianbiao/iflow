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

## 优化：增加工作流执行超时及 JOIN 等待超时保护

### 基本信息

- 类型：稳定性优化
- 优先级：P1
- 影响范围：所有同步执行的工作流

### 背景

当前运行状态已经支持 `TIMED_OUT`，前端模型也包含 `timeoutSeconds` 和 `timeoutPolicy`，但后端 `WorkflowDef` 没有对应字段，`DeciderService.checkWorkflowTimeout()` 目前为空实现。

因此，无论是 JOIN 长时间等待、分支任务阻塞，还是其他执行异常，工作流都可能无限保持 `RUNNING`。

配置校验应优先实施。超时是运行时最后一道保护，不能用来掩盖 `joinOn` 配置错误。

### 期望结果

每个工作流都有明确、可配置并且实际生效的执行期限。超过期限后，工作流进入 `TIMED_OUT`，持久化完整原因并结束同步调用。

### 建议方案

1. 在后端 `WorkflowDef` 中增加并持久化 `timeoutSeconds` 和 `timeoutPolicy`。
2. 未配置超时时间时使用系统级默认值，建议默认 `3600` 秒，并允许通过配置文件调整。
3. 将 `timeoutSeconds=0` 定义为继承系统默认值，避免无意配置成无限等待。
4. 每轮 Decider 执行前检查工作流总耗时。
5. JOIN 等待时检查工作流剩余时间，并记录仍未完成的等待任务。
6. HTTP、JDBC、WebService 等阻塞任务的连接和读取超时不得超过工作流剩余时间。
7. 超时后执行以下状态变更：
   - 当前等待或阻塞任务标记为 `TIMED_OUT`；
   - 其他非终态任务标记为 `CANCELED`；
   - 工作流标记为 `TIMED_OUT`；
   - 设置工作流和相关任务的 `endTime`；
   - 持久化超时原因；
   - 结束同步调用，避免执行线程继续空转。

建议超时信息：

```text
工作流执行超过 300 秒，等待 JOIN apply_join_ref；未完成任务：sync_lis_ref(IN_PROGRESS)
```

### 超时配置示例

```json
{
  "name": "SyncPacsLisNewApply",
  "version": 1,
  "timeoutSeconds": 300,
  "timeoutPolicy": "TIME_OUT_WF"
}
```

系统默认配置示例：

```yaml
iflow:
  workflow-execution:
    default-timeout-seconds: 3600
```

### 验收标准

- [ ] 工作流定义支持配置、保存和读取超时字段。
- [ ] 工作流执行快照包含实际生效的超时配置。
- [ ] 未配置或配置为 `0` 时自动应用系统默认超时。
- [ ] 达到超时时间后，工作流在可控误差内进入 `TIMED_OUT`。
- [ ] JOIN 超时时，原因中包含 JOIN 引用名以及未完成或不存在的等待引用。
- [ ] 所有非终态任务均被正确结束或取消。
- [ ] 超时状态和原因能够在执行详情页面查看。
- [ ] 同步请求能够结束，不再无限阻塞。
- [ ] 正常完成的工作流不会被误判为超时。
- [ ] 覆盖 JOIN 等待超时、阻塞分支超时、默认配置和正常完成的测试。

## 实施顺序

1. 增加工作流定义保存时的 JOIN 引用校验。
2. 增加 JOIN 运行时缺失引用的快速失败保护。
3. 打通工作流超时字段的前后端模型和持久化。
4. 实现工作流总超时检查及状态收敛。
5. 补充阻塞任务的剩余时间限制和完整回归测试。
