# 自动化配置 Skill

> 通过 MCP 接口完成接口对接的自动化配置

---

## 执行流程

```
0. 前置校验 → 检查本地服务是否启动
       ↓
1. 分析出入参 → 确定字段映射关系
       ↓
2. 创建节点 → 获取 nodeId
       ↓
3. 导入参数映射 → 前置 + 后置
       ↓
4. 创建 Mock → 获取 mockId
       ↓
5. 执行验证 → 查询日志确认映射正确
       ↓
6. 创建工作流 → 关联节点
```

---

## 步骤0：前置校验（必须）

在调用任何 MCP 接口前，**必须先校验本地服务是否启动**。

### MCP 健康检查（推荐）

**POST /mcp** — `tools/call`：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "agent_health",
    "arguments": { "action": "check" }
  }
}
```

| action | 说明 |
|--------|------|
| `ping` | 仅确认 MCP 进程存活（不查库） |
| `check` | 确认 MCP + agent 数据源可用（**自动化配置前用这个**） |

成功时 `result.content[0].text` 内 `ResultData.rsp.status` 为 `UP`；`database` 为 `UP` 表示数据库正常。

### 校验逻辑

```
agent_health.check 返回 status=UP 且 database=UP → 继续执行
     ↓
否则 → 停止执行，提示用户启动服务或检查数据源配置
```

### 服务未启动提示

```
⚠️ 本地服务未启动！

请先启动 interface-agent 服务后再执行自动化配置。
确认服务启动后，请重新发起配置请求。
```

---

## 对接方式

AI 自动化配置统一走 **MCP Streamable HTTP**（规范 `2025-11-25`，JSON-RPC 2.0 over HTTP）：

- 协议端点：`POST /mcp`（同一路径支持 `DELETE` 终止会话；`GET` 返回 405，本 Server 不提供 SSE 流）
- 握手：`initialize` → 读取响应头 `MCP-Session-Id`、`MCP-Protocol-Version` → 发送 `notifications/initialized`（返回 `202 Accepted`）
- 后续请求建议携带：`MCP-Session-Id`、`MCP-Protocol-Version`、`Accept: application/json, text/event-stream`
- 支持方法：`initialize` / `tools/list` / `tools/call` / `ping`
- 工具粒度：8 个域级 Tool，每个 Tool 用 `action` 字段区分 CRUD
  | Tool | 覆盖能力 |
  |------|---------|
  | `agent_health` | 健康检查 ping / check（配置前必调） |
  | `agent_workflow` | 工作流 CRUD |
  | `agent_workflow_node` | 工作流节点 CRUD |
  | `agent_node_param_config` | 参数映射 list/export/import/update/delete/delete_all |
  | `agent_mock_config` | Mock 配置 CRUD + delete_by_node |
  | `agent_mock_invoke` | Mock 链路验证 invoke / batch_invoke |
  | `agent_invoke_log` | 日志查询 list/latest/by_keyword/by_node |
  | `agent_spi_extension` | SPI 扩展 list/register/update/delete/enable/disable |

启用/关闭通过 `interface-agent.mcp.enabled` 控制（默认开启），路径可通过 `interface-agent.mcp.base-path` 调整；协议版本默认 `2025-11-25`（兼容协商 `2024-11-05` 等旧版客户端）。

**推荐握手顺序**（新客户端）：

1. `POST /mcp` — `initialize`（params 含 `protocolVersion: "2025-11-25"`）
2. 读取响应头 `MCP-Session-Id`，后续请求带上该头
3. `POST /mcp` — `notifications/initialized`（无 id，响应 202）
4. 再调用 `tools/call` 等

**MCP 错误返回约定**（Tool 业务失败时）：

```json
{
  "result": {
    "isError": true,
    "error": "[agent_workflow_node.create] 缺少必填字段: nodeName",
    "content": [{
      "type": "text",
      "text": "{\"success\":false,\"code\":-1,\"error\":\"缺少必填字段: nodeName\",\"message\":\"缺少必填字段: nodeName\",\"tool\":\"agent_workflow_node\",\"action\":\"create\"}"
    }]
  }
}
```

- 优先读 `result.error` 或 `content[0].text` 里的 `error` / `message` 字段，据此修正参数后重试
- 协议级错误（method 不存在、params 缺失）走 JSON-RPC `error` 字段

---

## 步骤1：创建节点

MCP `agent_workflow_node` create（**`flowId` 可选**；节点与工作流的关联主要靠创建工作流时的 `firstFlowNodes`）：

```json
{
  "action": "create",
  "nodeName": "业务描述名称",
  "nodeType": "HTTP",
  "metaInfo": "{\"url\":\"http://xxx\",\"method\":\"POST\"}",
  "keywordField": "patientCode"
}
```

**nodeType 选择**：
- HTTP → RESTful API
- WEBSERVICE → SOAP 接口
- DATABASE → 数据库直连

**响应**：`{"code":"200","rsp":8}` → rsp 就是 nodeId

---

## 步骤2：导入参数映射

MCP `agent_node_param_config` import：

```json
{
  "action": "import",
  "nodeId": 8,
  "clearBeforeImport": true,
  "params": [
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "patientCode",
      "sourceParamType": "STRING",
      "targetParamKey": "patNo",
      "targetParamType": "STRING",
      "mappingType": "NAME"
    }
  ]
}
```

### 前置处理模板（上游→下游）

**NAME 映射**：
```json
{
  "processType": "PRE_PROCESS",
  "sourceParamKey": "patientCode",
  "sourceParamType": "STRING",
  "targetParamKey": "patNo",
  "targetParamType": "STRING",
  "mappingType": "NAME"
}
```

**EXPRESSION 映射**：
```json
{
  "processType": "PRE_PROCESS",
  "sourceParamKey": "sex",
  "sourceParamType": "STRING",
  "targetParamKey": "gender",
  "targetParamType": "STRING",
  "mappingType": "EXPRESSION",
  "mappingRule": "#data == '1' ? '男' : '女'"
}
```

**CONSTANT 映射**：
```json
{
  "processType": "PRE_PROCESS",
  "sourceParamType": "NONE",
  "targetParamKey": "source",
  "targetParamType": "STRING",
  "mappingType": "CONSTANT",
  "mappingRule": "JARVIS"
}
```

### 后置处理模板（下游→上游）

**状态码转换**：
```json
{
  "processType": "POST_PROCESS",
  "sourceParamType": "NONE",
  "targetParamKey": "code",
  "targetParamType": "STRING",
  "mappingType": "EXPRESSION",
  "mappingSource": "RESPONSE",
  "mappingRule": "#response[returnCode] == '1' ? '200' : '-1'"
}
```

**字段映射**：
```json
{
  "processType": "POST_PROCESS",
  "sourceParamKey": "errorMessage",
  "sourceParamType": "STRING",
  "targetParamKey": "message",
  "targetParamType": "STRING",
  "mappingType": "NAME",
  "mappingSource": "RESPONSE"
}
```

### 嵌套对象

```json
{
  "processType": "PRE_PROCESS",
  "sourceParamType": "NONE",
  "targetParamKey": "patientInfo",
  "targetParamType": "OBJECT",
  "mappingType": "NAME",
  "childParams": [
    { "sourceParamKey": "code", ... },
    { "sourceParamKey": "name", ... }
  ]
}
```

### 数组（必须含虚拟节点）

```json
{
  "processType": "PRE_PROCESS",
  "sourceParamKey": "items",
  "sourceParamType": "ARRAY",
  "targetParamKey": "orders",
  "targetParamType": "ARRAY",
  "mappingType": "NAME",
  "childParams": [
    {
      "sourceParamType": "NONE",
      "targetParamKey": "orderNode",
      "targetParamType": "OBJECT",
      "mappingType": "NAME",
      "childParams": [
        { "sourceParamKey": "itemCode", "targetParamKey": "code", ... }
      ]
    }
  ]
}
```

---

## 步骤3：Mock 验证

### 创建 Mock

MCP `agent_mock_config` create：

```json
{
  "action": "create",
  "nodeId": 8,
  "caseName": "成功场景",
  "mockResponse": "{\"returnCode\":\"1\",\"message\":\"成功\"}"
}
```

### 执行调用

MCP `agent_mock_invoke` invoke：

```json
{
  "action": "invoke",
  "nodeId": 8,
  "mockId": 15,
  "businessData": {
    "patientCode": "TEST001",
    "patientName": "测试"
  }
}
```

### 查询日志

MCP `agent_invoke_log` latest：

```json
{
  "action": "latest",
  "limit": 1
}
```

**验证项**：
- `paramBeforeInvoke` → 前置映射结果
- `paramAfterInvoke` → 后置映射结果

---

## 步骤4：创建工作流

创建工作流时用 `firstFlowNodes` 关联已创建的 `nodeId`（此时不必给节点填 `flowId`）：

```json
{
  "action": "create",
  "flowName": "个人建档接口",
  "interfaceUri": "CreateArchives",
  "contentType": "JSON",
  "firstFlowNodes": [8],
  "status": 1
}
```

**interfaceUri 规则**：
- Jarvis 上游 → 使用具体的业务名称，如 CreateArchives
- 其他上游 → 使用 `/agent-open-api/**`

---

## 验证成功输出

```
✅ 配置完成！

节点：HIS建档接口 (nodeId: 8)
工作流：个人建档接口 (flowId: 10)

前置映射验证：
- patientCode → patNo: ✅
- sex "1" → gender "男": ✅

后置映射验证：
- returnCode "1" → code "200": ✅
```

---

## 验证失败处理

```
⚠️ 映射验证发现问题：

问题：性别转换未生效
- 期望：sex "1" → gender "男"
- 实际：gender "1"
- 原因：mappingType 应为 EXPRESSION

正在修复...
```
