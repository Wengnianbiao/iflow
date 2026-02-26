---
trigger: always_on
version: v3.0
last_updated: 2026-02-26
---

# Interface-Agent Project Knowledge

> 本文档描述框架的业务定位与领域模型事实：说明框架是做什么的，以及 3 个核心领域模型各自的字段与硬约束。

---

## 1. 框架定位

Interface-Agent 是上游系统与下游系统之间的接口中间层，核心职责如下：

1. 统一入口：通过 `interfaceUri` 接收请求并路由到对应工作流。
2. 协议适配：通过节点支持 `HTTP` / `WEBSERVICE` / `DATABASE` 调用。
3. 参数转换：通过参数映射完成双向结构转换。
4. 流程编排：通过工作流组织节点执行顺序，并支持调度与拆分。
5. 扩展能力：当上下游节点参数双向映射无法标准配置化时，通过 SPI 扩展远程调用行为。

数据流语义：

- `PRE_PROCESS`：上游请求 -> 下游请求
- `POST_PROCESS`：下游响应 -> 上游响应

---

## 2. 领域模型Workflow

Workflow 是对上游暴露的入口，按 `interfaceUri` 进行路由匹配。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| flowId | Integer | 自动 | 主键 |
| flowName | String | 是 | 工作流名称 |
| interfaceUri | String | 是 | 对外接口标识（路由键） |
| contentType | WorkflowContentType | 是 | 请求与响应主格式 |
| contentMetaInfo | String(JSON) | 否 | XML 解析辅助配置 |
| firstFlowNodes | List<Integer> | 是 | 首节点 ID 列表（支持并行） |
| status | Integer | 是 | 1 启用 / 0 禁用 |

### 2.1 Workflow 专属枚举

`WorkflowContentType` ：

- `JSON`
- `XML`

### 2.2 Workflow 专属规则

1. `interfaceUri` 是工作流路由关键字段。
2. 根据不同业务Jarvis 上游通常使用以下路径作为 `interfaceUri`，如：
   - `CreateArchives`
   - `QueryArchives`
   - `UpdateArchives`
   - `SyncHisChagSheet`
   - `SyncHisDelChagSheet`
   - `SyncHisRevokeChagSheet`
   - `SyncHisCancelRevokeChagSheet`
   - `SyncHisQueryChagSheetStatus`
   - `SyncHisQueryChagSheetRefundStatus`
   - `SyncPacsLisNewApply`
   - `SyncPacsLisCancelApply`
   - `GetItemResult`
3. 非 Jarvis 上游通常推荐使用 `/agent-open-api/**` 路径。
4. `firstFlowNodes` 为工作流起始节点集合，元素类型必须是节点 ID。
5. 当 `contentType=XML` 且需 XML 解析控制时，`contentMetaInfo` 可配置以下键用于定位业务数据：
   - `elementName`：业务数据主节点名，用于从 XML 中定位实际业务载荷（如 SOAP Body 内目标节点）。
   - `isCDATA`：是否按 CDATA 方式读取/写入业务数据；未配置时按 `true` 处理，适用于上游或下游要求 CDATA 包裹的场景。
   - `escape`：是否先做反转义后再解析（如 `&lt;`/`&gt;`/`&amp;`）；适用于响应内容被二次转义的场景。
   - `objectTag`：指定应按对象解析的标签名集合，避免单元素节点被误判为数组，保证对象/数组结构稳定。

---

## 3. WorkflowNode领域模型

WorkflowNode 是具体调用下游系统的节点单元，协议行为由 `nodeType` 与 `metaInfo` 决定。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| nodeId | Integer | 自动 | 主键 |
| nodeName | String | 是 | 节点名称 |
| flowId | Integer | 否 | 所属工作流 |
| nodeType | NodeType | 是 | 节点协议类型 |
| metaInfo | String(JSON) | 是 | 协议元配置 |
| keywordField | String | 否 | 日志关键字提取字段 |
| splitField | String | 否 | 数组拆分字段 |
| paramFilterExpr | String | 否 | 过滤表达式 |
| scheduleExpr | String | 否 | 下游节点调度表达式 |
| scheduleParamSourceType | ScheduleParamSourceType | 条件 | 配置 `scheduleExpr` 时必填 |
| remoteExtension | String | 否 | SPI 扩展名 |
| extensionParam | String(JSON) | 否 | SPI 扩展参数 |

### 3.1 WorkflowNode 专属枚举

`NodeType` ：

- `HTTP`
- `WEBSERVICE`
- `DATABASE`
- `NONE`

`ScheduleParamSourceType` ：

- `ORIGINAL`
- `PRE_RESPONSE`

### 3.2 `metaInfo` 规则（按 `nodeType`）

#### HTTP

| 字段 | 必填 | 说明 |
|------|------|------|
| url | 是 | 完整 URL |
| method | 否 | 默认 `POST` |
| headers | 否 | 默认 `Content-Type: application/json` |

```json
{
  "url": "http://example.com/api",
  "method": "POST",
  "headers": {
    "Content-Type": "application/json"
  }
}
```

#### WEBSERVICE

| 字段 | 必填 | 作用阶段 | 说明 |
|------|------|----------|------|
| url | 是 | 入参封装 | SOAP 地址 |
| headers | 否 | 入参封装 | 请求头 |
| soupTemplate | 是 | 入参封装 | SOAP 模板，`${inputParam}` 为占位符 |
| dataType | 否 | 入参封装 + 条件出参解析 | `json`/`xml`，默认 `xml`；用于请求体封装；在响应解析阶段仅当配置 `elementName` 且按文本/CDATA 抽取内容时，决定按 JSON 还是 XML 解析该内容 |
| elementName | 否 | 出参解析 | 响应业务数据标签定位 |
| isCDATA | 否 | 出参解析 | 默认 `true`，控制按 CDATA/文本读取 |
| escape | 否 | 出参解析 | 是否先反转义再解析 |
| objectTag | 否 | 出参解析 | 指定按对象解析的标签名列表 |

说明：

1. `responseType` 是独立键，用于后置映射完成后的结果结构格式化（`json`/`xml`），不用于 SOAP 原始响应解析。
2. WEBSERVICE 的原始响应解析由 `elementName`、`isCDATA`、`escape`、`objectTag`（以及条件下的 `dataType`）共同决定。

```json
{
  "url": "http://example.com/soap",
  "headers": {
    "Content-Type": "text/xml;charset=UTF-8"
  },
  "soupTemplate": "<soapenv:Envelope>...${inputParam}...</soapenv:Envelope>",
  "dataType": "xml",
  "elementName": "request",
  "isCDATA": true,
  "escape": false,
  "objectTag": [
    "PatientInfo"
  ]
}
```

#### DATABASE

| 字段 | 必填 | 说明 |
|------|------|------|
| driverClassName | 是 | JDBC 驱动类全限定名（非厂商简称） |
| url | 是 | JDBC URL |
| username | 是 | 用户名 |
| password | 是 | 密码 |
| operation | 是 | `SELECT`/`INSERT`/`UPDATE`/`DELETE` |
| sqlTemplate | 是 | SQL 模板，使用 `{paramName}` 占位 |
| responseType | 否 | 可选响应格式标识 |

常见驱动类：

- MySQL：`com.mysql.cj.jdbc.Driver`
- SQLServer：`com.microsoft.sqlserver.jdbc.SQLServerDriver`
- Oracle：`oracle.jdbc.OracleDriver`
- OpenGauss：`org.opengauss.Driver`

数据库响应约定：

- `SELECT`：`{"data": [...], "total": 行数}`
- 非 `SELECT`：`{"affectedRows": 影响行数}`

### 3.3 WorkflowNode 专属特殊规则

1. `splitField` 用于把数组入参拆分成多次调用，适用于下游不支持批量数组时。
2. `paramFilterExpr` 在节点执行前生效，表达式上下文为 `#data`；用于过滤或裁剪业务入参。
3. `paramFilterExpr` 的过滤结果以 `#data` 变更结果为准（表达式返回值不直接作为过滤结果）。
4. 配置了 `paramFilterExpr` 且过滤后数据为空时，节点将跳过调用并返回空结果。
5. `scheduleExpr` 必须返回 `List<Integer>` 或 `null`。
6. 配置 `scheduleExpr` 时，必须配置 `scheduleParamSourceType`，明确调度时的入参来源。
7. `remoteExtension + extensionParam` 用于标准配置外的 SPI 扩展调用。

`paramFilterExpr` 示例：

```spel
#data['status'] == 'ACTIVE' ? #data : #data.clear()
```

`scheduleExpr` 示例：

```spel
#data[code] == '200' ? T(java.util.Arrays).asList(2) : null
#data[code] == '200' ? T(java.util.Arrays).asList(2, 3) : T(java.util.Arrays).asList(4)
```

---

## 4. 领域模型三：ParamMapping（参数映射模型）

ParamMapping 描述字段级转换规则，支撑 PRE/POST 双阶段转换。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| configId | Integer | 自动 | 主键 |
| nodeId | Integer | 是 | 所属节点 |
| processType | ProcessType | 是 | PRE_PROCESS / POST_PROCESS |
| parentId | Integer | 否 | 父配置 ID |
| sourceParamKey | String | 条件 | `sourceParamType != NONE` 时必填 |
| sourceParamType | ParamType | 是 | 源字段类型 |
| targetParamKey | String | 是 | 目标字段名 |
| targetParamType | ParamType | 是 | 目标字段类型 |
| mappingType | MappingType | 是 | 映射方式 |
| mappingSource | MappingSource | 条件 | 仅 POST_PROCESS 生效 |
| mappingRule | String | 条件 | 按 `mappingType` 决定是否必填 |
| keepSingleElementKey | Boolean | 否 | 单元素数组是否保留 key |
| paramDesc | String | 否 | 描述 |
| sort | Integer | 否 | 排序 |

### 4.1 ParamMapping 专属枚举

`ProcessType` 白名单：

- `PRE_PROCESS`
- `POST_PROCESS`

`ParamType` 白名单：

- `STRING`
- `INTEGER`
- `LONG`
- `DOUBLE`
- `BOOLEAN`
- `OBJECT`
- `ARRAY`
- `PURE_ARRAY`
- `NONE`

`MappingType` 白名单：

- `NAME`
- `CONSTANT`
- `EXPRESSION`
- `BEAN_EXPRESSION`
- `SINGLE_MAP`

`MappingSource` 白名单（仅 POST_PROCESS）：

- `RESPONSE`
- `INPUT`

### 4.2 ParamMapping 专属特殊规则

1. `mappingSource` 只在 `POST_PROCESS` 使用。
2. 父节点确定 `mappingSource` 后，子节点应保持同一来源。
3. 跨来源混用时，应使用表达式显式访问 `#response[...]` 或 `#input[...]`。
4. `mappingRule` 约束：
   - `NAME` / `SINGLE_MAP`：通常无需 `mappingRule`
   - `CONSTANT`：必须填写常量值
   - `EXPRESSION`：必须填写 SpEL
   - `BEAN_EXPRESSION`：必须填写 Bean 表达式
5. `ARRAY` / `PURE_ARRAY` 必须包含一层虚拟节点：

```text
ARRAY/PURE_ARRAY
  └── 虚拟节点(sourceParamType=NONE, targetParamType=OBJECT)
        └── 元素字段
```

建议虚拟节点名：`itemNode` / `orderNode` / `dataNode`。

### 4.3 SpEL 上下文（映射表达式）

| 变量 | PRE_PROCESS | POST_PROCESS | 说明 |
|------|-------------|--------------|------|
| `#data` | 是 | 是 | 当前层级数据 |
| `#parent` | 是 | 否 | 父层级数据 |
| `#source` | 是 | 否 | 根层级数据 |
| `#input` | 否 | 是 | 原始输入 |
| `#response` | 否 | 是 | 下游响应 |

推荐写法：

```spel
#data[fieldName]
#data[patientInfo][patientCode]
#response[code] == '1' ? '200' : '-1'
#input[requestId]
```

说明：

1. 统一使用中括号路径风格，避免风格混用。
2. 表达式用于值转换，不应用于复杂流程控制。
