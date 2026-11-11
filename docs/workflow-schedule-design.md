# 工作流定时调度设计

一条定时计划按 Cron 和时区启动一个工作流。计划独立于工作流定义保存，同一工作流可以有多条计划。首版按现有 iFlow 单进程部署设计，通过控制台或 API 管理计划，MySQL 保存配置和下一次触发时间。

本地 Conductor 的 `WorkflowSchedule` 将 Cron、时区和 `StartWorkflowRequest` 放在同一条计划中，`SchedulerService` 计算下一次时间，到期后调用普通工作流启动服务。iFlow 沿用这个职责边界，使用一张 MySQL 表保存配置和时间游标，由进程内轮询器扫描到期计划。

## 启用

配置 `agent.repository.vendor=mysql` 时，定时调度默认启用。已有环境升级时，先在 iFlow 数据库执行[定时计划建表脚本](init_mysql/04_workflow_schedule.sql)再启动应用。需要关闭时设置 `iflow.scheduler.enabled=false`。

在控制台侧栏打开「配置管理 → 定时计划」，可新建、编辑、启用、停用和删除计划，并查看下次触发时间。编辑时可以选用 Cron 模板，预览未来五次触发在计划时区和浏览器本地时区的时间。非 Web 宿主也可执行已保存的计划。

## 配置契约

```json
{
  "name": "sync_lab_results",
  "displayName": "每天同步检验结果",
  "cronExpression": "0 0 8 * * *",
  "zoneId": "Asia/Shanghai",
  "enabled": true,
  "startWorkflowRequest": {
    "route": "lis/sync-results",
    "version": 1,
    "input": {
      "source": "LIS"
    }
  }
}
```

这条计划每天北京时间 08:00 启动 `lis/sync-results` 的第 1 版。Cron 使用与 Conductor 相同的 Spring 六字段格式，第一字段为秒。时区和启用状态显式填写，保存时校验 `ZoneId`，并确认 Cron 可以算出未来触发点。

`name` 是计划名称，全局唯一，只含英文字母、数字和下划线，最长 255 个字符。`preview` 和 `new` 保留给现有路径，不能当作计划名称。`displayName` 是展示名称，必填，可以是中文，最长 255 个字符，不要求唯一。工作流按 `route` 和 `version` 定位，保存时用现有元数据服务确认目标存在。省略版本时，每次触发都由启动服务解析最新版本。省略 `input` 时使用空对象，配置中的业务字段直接进入 `${workflow.input}`。详见[工作流启动约定](workflow-identity.md#接口约定)。

## 管理与存储

控制台 API 使用 `/api/workflow/schedules`。集合支持 `GET` 和 `POST`，`/{name}` 支持 `GET`、`PUT` 和 `DELETE`。通过 `PUT` 修改完整计划，包括启停和计划名称。路径中的 `name` 是当前计划名称，请求体中的 `name` 是保存后的计划名称。响应返回只读的 `nextRunAt`，用 ISO 8601 UTC 时间表示，停用时为 `null`。

`GET /api/workflow/schedules/preview` 接收 `cronExpression` 和 `zoneId`，按实际调度规则计算未来五次触发，返回 UTC Epoch 毫秒。

MySQL 的 `workflow_schedule` 以计划名称 `name` 为主键。表中还存放展示名称、Cron、时区、启用状态、工作流路由、可空版本和 JSON 入参，以及下一次触发时间。已经执行过带自增 `id` 的旧脚本时，先删除该表再执行新脚本；这张表只保存计划配置。

| 字段 | 用途 |
| --- | --- |
| `name` | 计划名称，主键，API 和控制台都按它寻址 |
| `display_name` | 展示名称，可含中文，不参与寻址和领取 |
| `next_run_at` | 下一次计划触发时刻，保存为 UTC Epoch 毫秒，停用时为 `NULL` |

到期扫描使用 `(enabled, next_run_at)` 索引。创建或更新启用的计划时，从本次保存时刻计算下一次触发。停用清空游标，删除移除记录。更新配置时校验读取到的计划名称和游标。如果领取或其他保存已经改变游标，重新读取并计算下一次时间，避免写回已领取的时点。修改展示名称不影响已经读到的触发。修改 Cron、时区、工作流或入参，以及停用或删除，会在领取前拦截本次触发。已领取的触发和已创建的工作流实例会继续执行。

## 触发流程

1. 应用就绪后启动一个后台轮询器，记录就绪时刻 `readyAt`。轮询器按 `next_run_at, name` 查询最多 100 条启用且到期的计划，逐条处理。每轮结束后间隔一秒再扫描，应用关闭时停止领取。
2. 对每条到期计划，按其时区计算下一次 Cron。游标在 `readyAt` 或更早时，只推进到 `readyAt` 之后的第一个计划点，跳过应用未就绪期间的触发。其他到期游标可以触发一次，游标直接推进到处理时刻之后的第一个计划点，合并运行期间积压的触发。
3. 通过一条条件更新推进游标。条件包含计划名称、启用状态、原游标，以及这次要启动的 Cron、时区、工作流路由、版本和入参。更新提交后，只有成功推进的一方才能启动这次应触发的工作流。

   ```sql
   UPDATE workflow_schedule
      SET next_run_at = :nextRunAt
    WHERE name = :name
      AND enabled = 1
      AND next_run_at = :expectedRunAt
      AND cron_expression = :cronExpression
      AND zone_id = :zoneId
      AND workflow_route = :workflowRoute
      AND (workflow_version = :workflowVersion OR (workflow_version IS NULL AND :workflowVersion IS NULL))
      AND input_json = :inputJson
   ```

4. 对成功领取的触发，直接调用 `WorkflowExecutionService.startWorkflow(request)`。该入口创建实例并在调用线程完成首次决策，再返回 `workflowId`。下一时点到期时可以创建新实例，即使上一次实例仍在执行。记录计划名称、原计划时刻以及实例 ID 或启动错误，业务执行结果通过现有工作流实例详情查看。

领取和启动之间存在故障窗口。如果领取已提交，进程随后退出或启动抛出异常，这个时点可能漏触发，轮询器从下一时点继续。首版对每次领取只发起一次启动调用。轮询器在独立线程上执行首次决策，因此某个工作流的首次决策耗时也会推迟同批其他计划。定义在保存之后被删除时，该次启动按现有规则失败并记录错误，后续时点继续按计划尝试。

## 模块归属

| 模块 | 职责 |
| --- | --- |
| `iflow-gateway` | 在独立的定时触发包中负责计划校验、Cron 计算和轮询，复用现有工作流启动服务。装配独立于 Servlet 网关 |
| `iflow-repository` | MySQL 计划存储、到期查询和条件更新 |
| `iflow-console` | 计划管理 API，复用同一个计划服务 |
| `iflow-spring-boot-starter` / `iflow-demo` | 装配默认启用的 MySQL 定时触发功能 |
| `iflow-ui` | 定时计划列表和编辑页 |

核心引擎继续负责解析工作流定义、创建实例和推进任务。计划与工作流实例分别遵循各自的重启语义，已启动实例沿用[工作流重启收尾规则](workflow-timeout.md#sweeper-的两个职责)。

## 验收场景

- 配置每天北京时间 08:00 的计划。到点后按所选路由、版本和入参创建一个实例。省略版本时，发布新版本后下一次触发使用新版本。
- 无效 Cron、时区、无未来触发点或不存在的工作流应在保存时拒绝。
- 同一时点被两次扫描时只有一次领取成功。修改 Cron、时区、工作流或入参，以及停用或删除后，旧扫描结果无法领取。只修改展示名称不影响这次领取。
- 停机期间的时点被跳过，恢复后从首个有效时点继续。运行中积压多个时点时，扫描恢复后每条计划最多触发一次积压。
- 启动抛出异常时记录诊断信息，同批其他计划继续处理，该计划的下一时点仍可触发。
