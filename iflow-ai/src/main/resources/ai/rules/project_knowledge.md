---
trigger: always_on
version: v6.0
last_updated: 2026-07-06
audience: ai_agent
---

# iFlow — MCP 核心（待接入 Conductor 新范式）

> **当前状态**：`iflow-ai` 仅保留 MCP Server 骨架（JSON-RPC、Session、Tool Registry）。
> 旧领域 Tool（Workflow / WorkflowNode / NodeParamConfig / Mock / SPI 等）已移除，后续按
> `meta_workflow_def` + `WorkflowDef` 新范式重新注册 Tool。

## MCP 架构

| 组件 | 职责 |
|------|------|
| `McpServerEndpoint` | HTTP/SSE 传输入口 |
| `McpRequestProcessor` | `initialize` / `tools/list` / `tools/call` 分发 |
| `McpToolRegistry` | 启动加载 `classpath:mcp/tool-schemas.json`，与 `McpToolDispatcher` Bean 按 name 配对 |
| `McpToolDispatcher` | 单个 Tool 的实现接口 |
| `McpToolPayload` | Tool 成功/失败统一 JSON 包装 |

## 扩展新 Tool 的步骤

1. 在 `mcp/tool-schemas.json` 的 `tools` 数组声明 `name`、`inputSchema`、`outputSchema`。
2. 实现 `McpToolDispatcher`（继承 `McpDispatcherSupport`），`@Component` 注册，`toolName()` 与 schema 一致。
3. 启动时 `McpToolRegistry` 会校验 schema 与 Dispatcher 一一对应，缺一则启动失败。

## 当前已注册 Tool

| Tool | 说明 |
|------|------|
| `agent_health` | `ping` / `check` — MCP 进程与可选 DataSource 探测 |

## 工作流定义新范式（后续 Tool 应对齐）

- 表：`meta_workflow_def`，主键 `(unique_name, version)`，无 surrogate `id`。
- 标识：`uniqueName`（ASCII，Conductor 风格）+ `name`（展示名，可中文）。
- 持久化：整份 `WorkflowDef` JSON 存入 `json_data`。
- 查询/API：以 `uniqueName` 为键，而非旧 `flowId` / `Workflow` 领域模型。
