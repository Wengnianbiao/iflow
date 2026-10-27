# 工作流路由与名称

工作流定义使用 `route` 表示调度标识，使用 `name` 表示业务名称：

```json
{
  "route": "GetItemResult",
  "version": 1,
  "name": "获取检验结果",
  "description": "查询检验结果",
  "tasks": []
}
```

`route + version` 唯一确定一份定义，对应 `meta_workflow_def` 的联合主键。路由以字母或数字开头，仅包含 `A-Za-z0-9._/-`，长度最多 255 个字符。`name` 必填、允许中文和重复，不参与调度；名称随版本定义保存，执行实例使用启动时的定义快照。

表单与 JSON 均可修改路由。已保存工作流修改路由后保存，按编辑中的路由和版本创建新工作流，原工作流保留，业务名称和任务名称不自动修改；与 Conductor 修改名称后新增的行为一致。路由和版本均未变化时只更新当前定义；只修改版本时通过创建接口保存同一路由的新版本。创建时联合主键重复则报错，不转为更新。复制工作流的副本版本从 1 开始，默认名称加“副本”。

## 接口约定

- 定义查询：`GET /api/workflow/metadata/detail?route=GetItemResult&version=1`。
- 最新定义及版本列表：`GET /api/workflow/metadata/latest?route=GetItemResult`、`GET /api/workflow/metadata/versions?route=GetItemResult`。
- 创建定义：`POST /api/workflow/metadata/create`，请求体为上述定义格式，联合主键重复时报错。
- 更新定义：`PUT /api/workflow/metadata?route=GetItemResult&version=1`，请求体的 `route/version` 必须与目标一致，目标必须已存在。
- 删除定义：`DELETE /api/workflow/metadata?route=GetItemResult&version=1`；省略版本删除该路由的全部版本。
- 启动：`POST /api/workflow/execution/start`，请求体为 `{"route":"GetItemResult","version":1,"input":{}}`；省略版本取该路由的最新版本。
- 查询实例：`GET /api/workflow/execution/instances?route=GetItemResult&version=1`。
- 搜索：单选使用 `workflowRoute`，多选使用重复的 `workflowRoutes` 参数；控制台分享链接使用重复的 `workflowRoute` 参数。

执行摘要和运行时对象的 `workflowRoute` 表示路由，`workflowName` 表示业务名称，实例身份仍为 `workflowId`。任务的 `name` 保持任务名称语义；任务运行时的 `workflowType` 和 `${workflow.workflowType}` 表达式取工作流路由。

REST 网关支持任意业务路径的 GET / POST 请求，不要求 `hl-open-api` 前缀。去掉应用 context path 和请求路径的前导 `/` 后，与工作流 `route` 精确匹配，默认使用最新版本。例如：

| 请求路径 | 工作流 route |
| --- | --- |
| `/pacs/apply-status` | `pacs/apply-status` |
| `/GetItemResult` | `GetItemResult` |
| `/hl-open-api/pacs/apply-status` | `hl-open-api/pacs/apply-status` |

`*` / `**` 表示 HTTP 入口可以接收不同路径，工作流 `route` 本身仍是精确标识，不支持通配符。已有 `hl-open-api/...` 路由保持可用；改为新路径时需要保存对应 route 的定义。

根路径、`/index.html`、`/favicon.svg` 以及 `/api/**`、`/assets/**`、`/error/**`、`/actuator/**`、`/HLOpenApi/**` 保留给控制台、静态资源、应用基础设施和 Jarvis；其他明确声明的 Controller 路径优先于通用 HTTP 工作流入口。所有业务路径使用相同的请求体解析、体积限制和 multipart 错误处理。

Jarvis 兼容入口仍为 `POST /HLOpenApi/Hjk`，按请求体中的 `BusinessMethod` 匹配工作流 route，默认使用最新版本；不按该 URL 查找工作流。

## HTTP 工作流入参

通用 REST 的工作流输入包含 `body`、`rawBody`、`query`、`headers`、`files`、`method`、`path`。`body` 保存解析后的请求体，业务字段不再直接放在工作流输入顶层。

Jarvis 的 Controller 接收 `HttpServletRequest`，使用独立的 `JarvisRequestReader` 读取固定 JSON 对象请求体，工作流输入包含 `body` 和 `rawBody`，保留 `BusinessMethod`、`BusinessCode`、`data` 等字段的原始名称和层级，不只提取 `data`。

例如 Jarvis 请求 `{"BusinessMethod":"AddPacsApply","BusinessCode":"PACS","data":{"id":"001"}}`，工作流统一使用 `${workflow.input.body.BusinessCode}`、`${workflow.input.body.data.id}`。已有 Jarvis 工作流需将 `${workflow.input.BusinessCode}`、`${workflow.input.data.id}` 等引用改为 `body` 下的路径；本次不保留顶层业务字段副本。

通用 REST 请求体始终按 `Content-Type` 解析：JSON 保留原生值，XML 解析为对象，URL 编码表单解析为字段，`text/plain` 保留字符串。`X-iFlow-Body-Mode` 不再控制解析，也不能跳过 JSON / XML 格式校验。Jarvis 直接将请求流解析为 JSON 对象，只校验 JSON 对象格式和非空字符串 `BusinessMethod`，不使用通用 REST 的请求读取和体积限制配置。

通用 REST 和 Jarvis 始终提供 `rawBody`，无需配置开关。`rawBody` 为按请求字符编码解码的原始文本，保留换行、空格和引号；它不替换解析后的 `body`，也不参与路由。GET 和空请求体的 `rawBody` 为空字符串。multipart 继续使用 `body` 保存表单字段、`files` 保存文件数据，`rawBody` 为 `null`，不保存整个 multipart 原文。

控制台“执行工作流”页面的 Body 区域采用 Postman 的交互方式，支持无请求体、`form-data`、`x-www-form-urlencoded` 和 `raw`；`raw` 可选择 JSON、XML 或纯文本，表单以键值行编辑，`form-data` 可选择文本或文件。页面通过 `POST /api/workflow/execution/start-http?route=...&version=...` 发送请求体及对应的 `Content-Type`，复用网关读取器生成 `body`、`rawBody` 等入参，再异步启动所选路由和版本并返回实例 ID。`path` 使用 `/<route>`，`query` 为空，启动参数不混入业务查询参数。恢复历史入参时优先使用保存的 `Content-Type` 和 `rawBody` 自动选择编辑模式；缺少请求头时识别报文格式，表单恢复键值行，文件从保存的 `contentBase64` 恢复。原 `/start` 接口继续接收完整工作流 JSON 入参。

项目未上线，接口及初始化建表脚本直接采用新格式。旧 `name/displayName` 协议和既有开发数据的迁移不在本次范围内。
