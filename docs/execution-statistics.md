# 执行索引与调度看板

左侧「调度看板」（`/#/dashboard`）是统计大盘：展示配置规模、工作流与任务运行指标、执行次数趋势和状态分布。默认查询今天、全部工作流，支持昨天、最近七天、自定义时间、工作流筛选与手动刷新。

- 配置总量保持全局口径：工作流按当前定义的 route 计数；任务递归统计各工作流最新版本的全部节点，包含控制节点、分支和循环体，循环轮次不重复计入。
- 运行区域同时展示工作流与全部任务的执行次数、未结束数量、成功率和平均耗时。工作流筛选仅影响运行区域。
- 趋势展示工作流与任务执行次数两条曲线；状态分布直接使用成功、失败、未结束数量。无数据时数量为 0，成功率和平均耗时为 `—`，状态图显示「暂无执行数据」。查询失败显示错误和不可用状态，不作为零值展示。
- 看板不展示列表、分页或实例明细；执行细节在「调度日志 → 工作流调度」查询。

## 执行索引

参考本仓库 Conductor 的 `IndexDAO` / `MySQLIndexDAO`，core 定义统一 `IndexDAO`，repository 提供 MySQL 实现。
`workflow_index`、`task_index` 是原始执行记录的轻量查询投影，由执行搜索、数据库分页、节点明细和看板聚合共用，不再维护看板专用统计表。

MySQL 实现与主执行记录同连接、同事务写入或删除索引；原始 `workflow`、`task` 表继续保存业务输入输出与定义快照。
工作流推进、超时扫描、详情读取均以原始记录为准。索引只保存摘要，较旧的 `updateTime` 不能覆盖新状态。
本期实现结构化条件搜索及 MySQL 存储，不引入全文检索语法或外部搜索服务。

「工作流调度」（`/#/executions`）支持切换工作流与工作流任务查询。任务视图可切换全部任务或外部调用，外部调用沿用索引中的异步分类。两种视图共用工作流名称、关键字及起始时间范围；任务关键字从所属工作流的 `correlationId` 关联查询和展示，不另存任务关键字。任务行可直接打开执行详情并定位具体任务。

## 指标口径

- 来源是持久化的工作流及任务实例，包含网关、控制台试运行、代码启动的执行。实例创建前被拒绝的请求不计入。
- 工作流按 `workflowId` 计次；任务按 `taskId` 计次，每次重试和循环尝试分别计数。
- 按起始时间筛选。工作流起点为 `startTime`，任务起点为 `startTime > 0 ? startTime : scheduledTime`；任务排队时先用调度时间，真正开始后切换为开始时间。
- 耗时与执行详情一致：终态结束时间减起点。成功、失败、超时、终止等终态均参与统计；时间缺失或结束早于起点的记录只计次数，不计耗时。未结束记录单独展示。
- 成功率为成功终态数除以全部终态数，遵循现有状态枚举（任务包括 `COMPLETED_WITH_ERRORS`、`SKIPPED` 的成功语义）。没有终态样本时显示 `—`。
- 工作流按 route 合并版本；任务按 route、定义中的节点引用名、任务类型合并。循环轮次及重试汇入同一节点行，同名节点跨工作流分开。
- 异步分类来自 `WorkflowSystemTask.isAsync()`，不是精确的外部 IO 时间。分类在首次同步时保存，未注册类型仅在全部任务中展示。
- 控制节点耗时可能包含等待分支的时间。各任务耗时不作加减以推算工作流调度耗时。
- 删除执行记录会同步删除相应统计索引，统计不会在清理执行记录后继续保留。

## 安装与启用

部署前执行 `docs/init_mysql/03_execution_index.sql`，新安装在 `02_schema.sql` 后执行。脚本仅创建 `workflow_index`、`task_index` 两张索引表，可重复执行。

应用启动后，创建或更新的执行实例会同步进入索引。执行搜索、最近执行列表和调度看板直接查询索引。
已有执行记录不自动扫描或回填；没有索引的旧记录不会出现在搜索及统计结果中。

索引与主执行记录使用同一数据库连接、同一事务维护。看板查询只聚合轻量字段，不读取任务输入输出。

## 接口

- `GET /api/workflow/execution/search`：工作流标识、多 route、版本、状态、correlationId、起始时间范围及分页。
- `GET /api/workflow/execution/tasks/search`：工作流（`workflowRoute` 或多选 `workflowRoutes`）、工作流实例、所属工作流的 `correlationId`、任务实例、节点引用、任务类型、状态、异步筛选、时间范围及分页。

任务搜索返回摘要及所属工作流名称、关键字，不携带业务报文；任务开始时间为实际开始时间，排队中的实例为 0。时间筛选使用实际开始时间，未开始时使用调度时间。未指定时间、实例 ID 或 correlationId 时，搜索默认最近七天。工作流与任务按 correlationId 查询均不限时间，仍可显式指定时间范围。

看板总览接口：

- `GET /api/workflow/statistics/overview`
- 必填 `startTime`、`endTime`（毫秒，含边界）、`timeZone`（浏览器的 IANA 时区，例如 `Asia/Shanghai`）；可选 `workflowRoute`。
- `ResultData.data` 包含全局 `workflowCount`、`taskCount`，以及筛选后的 `runtime`。`runtime.workflows`、`runtime.tasks` 复用现有指标；`runtime.granularity` 为 `hour`、`day` 或 `month`；`runtime.trend` 按时间升序返回 `{startTime, workflowCount, taskCount}`。
- 两天以内按小时、超过两天至九十天按天、更长按月分桶，跨度按含边界的实际时间计算。桶起点为浏览器时区对应的小时、日或月起点，首尾桶仅计入查询范围内记录，缺失桶补零。Java 计算日历边界，不依赖 MySQL 时区表；夏令时重复小时保留为不同时间戳。
- 总览不接受分页、排序或异步任务筛选；任务统计包含全部类型。未结束数量是所选范围内开始且尚未结束的实例，不是全部历史实例的当前存量。

以下既有聚合和任务明细接口保持兼容，调度看板前端不再使用：

- `GET /api/workflow/statistics/workflows`
- `GET /api/workflow/statistics/tasks`
- `GET /api/workflow/statistics/task-instances`

聚合接口要求 `startTime`、`endTime`（毫秒，含边界）；可选 `workflowRoute`、`asyncOnly`（任务默认 true）、`sort`、`descending`、`pageNum`、`pageSize`（最大100）。排序字段为 `totalCount`、`avgDurationMs`、`minDurationMs`、`maxDurationMs`、`successRate`。

响应沿用 `ResultData`，data 包含当前完整筛选范围的 `summary`、分页 `rows`、分组数 `total`。整体平均值按所有有效样本加权，不取分页行均值。任务明细另要求 `workflowRoute`、`nodeRef`、`taskType`，按起始时间倒序分页。
