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

REST 网关去掉请求路径的前导 `/` 后按路由调用；Jarvis 的 `BusinessMethod` 按路由调用，均默认使用最新版本。

项目未上线，接口及初始化建表脚本直接采用新格式。旧 `name/displayName` 协议和既有开发数据的迁移不在本次范围内。
