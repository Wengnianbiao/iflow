---
trigger: always_on
version: v5.2
last_updated: 2026-05-25
audience: ai_agent
---

# Interface-Agent 框架知识库（AI 配置与编排专用）

> **读者**：需要根据自然语言或接口说明**生成/修改**工作流、节点、`NodeParamConfig`，或**解释**框架行为的 AI。  
> **目标**：掌握**配置化规则**（字段必填、合法枚举、默认值）、**SPI 与配置的边界**、**SpEL 在各处的变量与返回值约束**、**调度/并行/拆批**语义，避免与运行时代码不一致的输出。  
> **核心三层模型**：`Workflow`（入口与路由）→ `WorkflowNode`（外呼协议与调度控制）→ `NodeParamConfig`（PRE/POST 字段映射）。

---

## 1. 路由与入口（配置必须先对上）

AI 生成 `interfaceUri` 时须区分两条入口的**匹配键**与**未命中行为**（`WorkFlowOrchestrator`）：

| 入口 | HTTP 路径（典型） | 与工作流 `interfaceUri` 的匹配方式 | 未命中工作流时 |
|------|------------------|-------------------------------------|----------------|
| **Open API** | `POST/GET /agent-open-api/**` | 使用 **完整请求 URI** 与 `interfaceUri` 匹配 | 报错（返回 JSON 错误体） |
| **Jarvis** | `POST /HLOpenApi/Hjk`（JSON：`JarvisRequest`） | 使用请求体 **`businessMethod`** 与 `interfaceUri` 匹配 | **默认成功**（`JarvisResultData.ok()`），不进入流程 |

**推论**：Jarvis 场景若 `interfaceUri` 配错，表现为「静默未接入」；Open API 场景则为显式错误。  
入口 `Content-Type` 可有 JSON/XML 等；工作流 **`contentType`** 决定 **请求体↔Map**、**响应 Map↔字符串** 的适配方式。

**JSON 响应特例**（`ContentTypeAdapter`）：当 `contentType=JSON` 且结果 Map **仅有键 `data`** 且值为**数组**（或数组类型），序列化时**直接输出数组 JSON**（不包一层对象）。

**GET（Open API）**：无 body 时，框架将 **QueryString** 收成 `Map` 再序列化为 JSON 字符串，当作请求体进入适配（`ApiController`）。

### 1.1 Jarvis：默认 Handler 的入参/出参约定（易漏）

以下描述 **`DefaultJarvisRouteHandler`**，自定义 `JarvisRouteHandler` SPI 可完全不同。

**入参 Map（`resolveJarvisRequest`）**：

| 条件 | 进入调度器的 `businessData` |
|------|-----------------------------|
| `businessMethod` **等于字符串** `"GetItemResult"` | 将整个 `JarvisRequest` **Bean → JSON → JSONObject**，再转为 Map（多字段平铺进 Map） |
| 其它 `businessMethod` | 仅 **`request.getData()`**（`JSONObject`）转 Map；**`data == null` ⇒ 得到 `null`**，后续调度可能 NPE，需保证有 `data` 或改用扩展 Handler |

**出参包装（`processJarvisRsp`）**：调度返回的 Map 若：

- **empty** ⇒ `JarvisResultData.ok()`（无业务体）；
- 非空 ⇒ 读取 **`code`**、**`message`**、**`rsp`**：
  - **`code` 等于字符串 `"200"`** ⇒ `JarvisResultData.ok(rsp)`；
  - 否则 ⇒ `JarvisResultData.error(message)`。

因此：**走默认 Jarvis Handler 时**，若希望失败可感知，最终 Map 须按上述键提供；成功时常放 **`code=200`** 与 **`rsp`**。

---

## 2. SPI 与配置化：分工与枚举

### 2.1 何时只配库表、何时必须 SPI

- **纯配置可完成**：`interfaceUri`、节点 `nodeType` + `metaInfo`、PRE/POST 映射、过滤/调度/拆批表达式（SpEL）、`responseType`、XML `contentMetaInfo` / SOAP 解析键等。
- **必须 SPI / 扩展注册**：入口协议与默认 `OpenApiRouteHandler` / `JarvisRouteHandler` **不一致**；外呼需**非标准客户端/签名/加解密**；需在 **`doInvoke` 链上**统一 Mock、改包头、日志策略等。

### 2.2 `ExtensionType`（与配置字段对应）

| 枚举 | code | SPI 接口（全限定名） | 多节点绑定 | 由谁触发 |
|------|------|----------------------|------------|----------|
| FILTER | `filter` | `com.helianhealth.agent.invocation.remote.filter.AgentFilter` | 是 | 节点 `extensionParam` 等参与 **过滤器链**（包在 `InterfaceClientProxy` 外） |
| REMOTE | `remote` | `com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker` | 是 | 节点 **`remoteExtension`** |
| WORKFLOW | `workflow` | `com.helianhealth.agent.api.OpenApiRouteHandler` | 否 | 工作流 **`routeExtension`** 参与自适应**入口** |
| JARVIS_WORKFLOW | `jarvis-workflow` | `com.helianhealth.agent.api.JarvisRouteHandler` | 否 | 同上（Jarvis 侧） |

`InterfaceRemoteInvoker`：`@AgentAdaptive("remoteExtension")` 读取 **`WorkflowNode.getRemoteExtension()`** 作为 SPI 名；**空 / 空白** 时回落 **`@AgentSPI("default")`**（仓库内现为 `DefaultInterfaceRemoteInvoker`）。

`InterfaceClientProxy`：`@AgentAdaptive("nodeType")` 同理，枚举名转小写（§4.1）。

`OpenApiRouteHandler` / `JarvisRouteHandler`：`@AgentAdaptive("routeExtension")` 使用 **`Workflow.getRouteExtension()`**；空则走默认 **`@AgentSPI("default")`** 实现（`DefaultOpenApiRouteHandler` / `DefaultJarvisRouteHandler`）。

---

## 3. 领域模型：`Workflow`

| 字段 | 类型 | 约束 | AI 说明 |
|------|------|------|---------|
| flowId | Integer | 主键 | 自增 |
| flowName | String | 必填 | 展示名 |
| interfaceUri | String | **必填** | **路由键**；Open API 常为小写全路径；Jarvis 常等于 `businessMethod` |
| routeExtension | String | 可选 | 与 **WORKFLOW / JARVIS_WORKFLOW** 类扩展配合，自定义入口编排 |
| contentType | enum | **必填** | `JSON` \| `XML` |
| contentMetaInfo | String(JSON) | 可选 | **主要为 XML**：请求解析 + 响应拼装（§3.1） |
| firstFlowNodes | 整数 ID 列表 | **必填** | 首跳节点，可多 id **并行**；须为已存在节点 |
| status | Integer | **必填** | `1` 启用 / `0` 禁用；**仅启用工作流会命中路由**（`selectByInterfaceUri` 带 `status = 1`，禁用等同「未配置」） |

### 3.1 `contentMetaInfo`（工作流级 XML）

**请求**（`XmlUtils.parseRequestXml`）：与 SOAP 解析共享键语义。

| JSON 键 | 作用 |
|---------|------|
| elementName | 外层唯一业务节点标签 |
| isCDATA | 默认 `true`：取文本/CDATA；`false` 则解析**第一个子元素** |
| escape | `true` 时先实体反转义再解析 |
| dataType | 与 `elementName` 联用：内容为 JSON 时用 `json`（大小写不敏感） |
| innerElementName | 内层 XML 再定位根标签 |
| objectTag | 避免单子元素被误判为数组 |

**响应**（`XmlUtils.buildResponseXml`）额外：

| JSON 键 | 作用 |
|---------|------|
| resultElementName | 根元素名；缺省用结果 Map 的第一个 key |
| responseNamespace | 根元素命名空间 |
| soapRspTemplate | SOAP 外壳；**`${outputParam}`** 替换为内层业务 XML |

---

## 4. 领域模型：`WorkflowNode`

| 字段 | 类型 | 约束 | AI 说明 |
|------|------|------|---------|
| nodeId | Integer | 主键 | 自增 |
| nodeName | String | 必填 | |
| flowId | Integer | 可选 | 归属工作流 |
| nodeType | enum | **必填** | `HTTP` \| `WEBSERVICE` \| `DATABASE` \| **`NONE`** |
| metaInfo | String(JSON) | 视类型 | HTTP/WS/DB 必填子键见 §5；`NONE` 可空 |
| keywordField | String | 可选 | 日志关键字来源字段 |
| splitField | String | 可选 | **数组**拆分字段：无 **`.`** 时为**顶层 key**，值必须是 **List**；含 **`.`** 时为**嵌套路径**（如 `order.items`），路径除最后一级外须为 **Map**，最后一级须为 **List**；不满足抛 **`BUSINESS_DATA_SPLIT_ERROR`**；每元素一次外呼后 **merge**（见 `BusinessDataSplitter`） |
| paramFilterExpr | String | 可选 | SpEL：**仅 `#data`**；**原地修改**深拷贝入参；若配置表达式且结果 **empty → 跳过本节点外呼** |
| scheduleExpr | String | 可选 | SpEL：**`#data` = 本节点 `doInvoke` 后的响应 Map**；返回值须为 **`List<Integer>`**（nodeId）或 **`null`** |
| scheduleParamSourceType | enum | **若存在 scheduleExpr 则必填** | `ORIGINAL`：下一步用**最初**工作流入参；`PRE_RESPONSE`：用**本节点响应**；缺省会 **`requireNonNull` NPE** |
| remoteExtension | String | 可选 | `InterfaceRemoteInvoker` 扩展名 |
| extensionParam | 字符串键值 Map | 可选 | 运行时 Map；**DB 存 JSON 字符串**；用于过滤器链与扩展参数 |

### 4.1 `nodeType` → `InterfaceClientProxy`（枚举名小写为 SPI 名）

| nodeType | SPI 名 | 行为要点 |
|----------|--------|----------|
| HTTP | `http` | `HttpProtocolAdapter` |
| WEBSERVICE | `webservice` | `WebServiceProtocolAdapter` |
| DATABASE | `database` | `DatabaseProtocolAdapter` |
| NONE | `none` | **不外呼**；`doInvoke` 返回 **空 Map `{}`**；透出数据靠 **POST_PROCESS** 从 `#input` / `#response` 拼 |

静态声明：`META-INF/agent/com.helianhealth.agent.invocation.remote.InterfaceClientProxy`。

---

## 5. `metaInfo`（节点协议 JSON）

键名以 **`MetaInfoKeyConstants`** 为准（下列为配置时应用的全集摘要）。

### 5.1 HTTP

| 键 | 必填 | 默认/说明 |
|----|------|-----------|
| url | **是** | |
| method | 否 | 默认 **`POST`**；支持 GET/PUT/DELETE 等 |
| headers | 否 | Map |
| responseType | 否 | 后置映射完成后：`json`（默认）或 `xml`，影响 `ProxyConvertHelper.convertResponse` |

### 5.2 WEBSERVICE

| 键 | 必填 | 说明 |
|----|------|------|
| url | **是** | SOAP 地址 |
| headers | 否 | |
| soupTemplate | **是** | 信封；**`${inputParam}`** 替换业务体 |
| dataType | 否 | 请求体封装：`json` / `xml` |
| responseType | 否 | 同 HTTP（后置结构） |
| elementName / innerElementName / isCDATA / escape / objectTag / dataType | 否 | **原始 SOAP 响应**解析（与 `XmlUtils` 一致） |
| soapUseCompatibleSender | 否 | `true`：`HttpURLConnection` 兼容发送 |

### 5.3 DATABASE

| 键 | 必填 | 说明 |
|----|------|------|
| driverClassName / url / username / password | **是** | JDBC |
| operation | **是** | `SELECT` \| `INSERT` \| `UPDATE` \| `DELETE` |
| sqlTemplate | **是** | **`{paramName}`** 占位 |
| responseType | 否 | 同 HTTP（后置） |

**解析后 Map 惯例**：`SELECT` → `data`（列表）+ `total`；写操作 → `affectedRows`。

---

## 6. `NodeParamConfig`（映射规则）

| 字段 | 约束 | AI 必须知道 |
|------|------|-------------|
| processType | **必填** | `PRE_PROCESS`（上游→下游请求体） / `POST_PROCESS`（下游响应→上游响应） |
| nodeId | **必填** | |
| parentId | 可选 | 树形父子 |
| sourceParamKey / targetParamKey | 条件 | `sourceParamType != NONE` 时常需 `sourceParamKey`；`targetParamKey` 通常必填 |
| sourceParamType / targetParamType | **必填** | `STRING,INTEGER,LONG,BOOLEAN,DOUBLE,OBJECT,ARRAY,PURE_ARRAY,NONE` |
| mappingType | **必填** | `NAME,CONSTANT,EXPRESSION,BEAN_EXPRESSION,SINGLE_MAP` |
| mappingSource | **仅 POST 有意义** | `RESPONSE` / `INPUT`；**父子宜同源**。**为 `null` 时**（对象/数组节点）：子树在 **当前入参 + 当前响应** 双上下文上继续构建（`DefaultParamTreeBuilder`，与显式指定单源不同） |
| mappingRule | 条件 | `mappingType` 为 CONSTANT / EXPRESSION / BEAN_EXPRESSION 等时需填 |
| keepSingleElementKey | 可选 | 单元素数组是否保留外层 key |
| sort | **必填** | 整数，常用 0 |

**`SINGLE_MAP`**：父级为 `ARRAY` 或 `PURE_ARRAY` 且元素为基础类型时，从包装 Map 取单值映射。

**`ARRAY` / `PURE_ARRAY`**：`PURE_ARRAY` **仅作源侧**（顶层即数组）；通常需 **`sourceParamType=NONE` + `targetParamType=OBJECT` 的虚拟父节点**，下挂元素；虚拟节点名惯例带 `Node` 后缀。

**`sourceParamKey`**：仅**源侧**支持 **点号路径** 钻取嵌套 Map（`patientInfo.hisOrgId`、`doctorInfo.number`，见 `ProxyConvertHelper.convertSourceValue`）。**SpEL 映射**仍建议 **`#data['key']`** 风格。

**`targetParamKey`**：**禁止含 `.`**。框架按参数树 `childParams` 递归拼装目标 JSON/XML，**一层 key 对应树上一级节点**；若写成 `Body.TjNo`，产出是字面量键 `"Body.TjNo"` 的**平铺 Map**，**不会**变成 `{ "Body": { "TjNo": ... } }`。

**`mappingType=NAME`**：源 key 与目标 key 按名拷贝（需类型与树结构匹配）。

### 6.1 嵌套目标结构：OBJECT + childParams（易错）

三方接口文档常写 `Body.TjNo`、`Head.TradeCode`，AI **不得**把点号写进 `targetParamKey`。应拆成 **OBJECT 父节点 + 子字段**：

| 文档路径 | 错误 ❌ | 正确 ✅ |
|----------|---------|---------|
| `Body.TjNo` | `"targetParamKey": "Body.TjNo"` | 父：`targetParamKey=Body`，`targetParamType=OBJECT`；子：`targetParamKey=TjNo` |
| `Head.TradeCode` | `"targetParamKey": "Head.TradeCode"` | 父：`targetParamKey=Head`，`targetParamType=OBJECT`；子：`targetParamKey=TradeCode` |

**错误产出示例**（禁止）：

```json
{
  "Body.TjNo": "2509050001",
  "Head.TradeCode": "PAT202"
}
```

**正确产出示例**：

```json
{
  "Body": {
    "TjNo": "2509050001",
    "patName": "测试账号"
  },
  "Head": {
    "TradeCode": "PAT202",
    "SystemCode": "TJ"
  }
}
```

**正确映射片段**（PRE_PROCESS，嵌套字段名为 `childParams`）：

```json
[
  {
    "processType": "PRE_PROCESS",
    "sourceParamType": "NONE",
    "targetParamKey": "Body",
    "targetParamType": "OBJECT",
    "mappingType": "NAME",
    "sort": 1,
    "childParams": [
      {
        "processType": "PRE_PROCESS",
        "sourceParamKey": "patientCode",
        "sourceParamType": "STRING",
        "targetParamKey": "TjNo",
        "targetParamType": "STRING",
        "mappingType": "NAME",
        "sort": 1
      },
      {
        "processType": "PRE_PROCESS",
        "sourceParamKey": "patientName",
        "sourceParamType": "STRING",
        "targetParamKey": "patName",
        "targetParamType": "STRING",
        "mappingType": "NAME",
        "sort": 2
      }
    ]
  },
  {
    "processType": "PRE_PROCESS",
    "sourceParamType": "NONE",
    "targetParamKey": "Head",
    "targetParamType": "OBJECT",
    "mappingType": "NAME",
    "sort": 2,
    "childParams": [
      {
        "processType": "PRE_PROCESS",
        "sourceParamType": "NONE",
        "targetParamKey": "TradeCode",
        "targetParamType": "STRING",
        "mappingType": "CONSTANT",
        "mappingRule": "PAT202",
        "sort": 1
      }
    ]
  }
]
```

**规则小结**：

- **`.` 只用于 `sourceParamKey`**（从上游嵌套数据取值），或 `splitField` / `keywordField` 等同理路径字段。
- **目标嵌套一律用 `targetParamType=OBJECT`（或 ARRAY）+ `childParams`**，子节点 `targetParamKey` 仅为**当前层**字段名。
- OBJECT 父节点若仅作结构容器、不映射源对象：`sourceParamType=NONE`，`mappingType=NAME`（常见写法）。

---

## 7. SpEL：按使用场景分列（禁止混用变量）

AI 写表达式时必须选用**正确上下文**与**返回类型**：

| 场景 | 工具方法/入口 | 变量 | 返回值约束 |
|------|----------------|------|------------|
| 字段映射 PRE | `SpelUtils.parserPreNode` / Bean 版 | `#data` 当前层，`#parent` 父层，`#source` 根入参 | 映射目标类型可接受值 |
| 字段映射 POST | `parserPostNode` / Bean 版 | `#data`，`#input` 原始请求，`#response` 下游响应 | 同上 |
| 节点入参过滤 | `SpelUtils.businessDataFilter` | **仅 `#data`**（入参深拷贝） | 副作用：修改 `#data`；空则跳过后续外呼 |
| **条件调度** | `SpelUtils.parseScheduleExpression` | **仅 `#data`**，且 **= 当前节点响应 Map**（非原始请求） | **`List<Integer>`** 或 **`null`**；元素必须 **Integer** |
| Bean 映射 | `parserPreNodeWithBeanAccess` 等 | 同上各场景 + `@beanName` | |

**建议路径写法**：`#data['key']` 或 `#data[key]`，与仓库示例一致。

**调度示例**（返回值类型必须满足）：

```spel
#data['code'] == '200' ? T(java.util.Arrays).asList(2, 3) : T(java.util.Arrays).asList(4)
```

**过滤示例**（空则跳过节点）：

```spel
(#tempItems = #data['items']?.?[departType == 'PACS']) == null || #tempItems.isEmpty() ? #data.clear() : #data.put('items', #tempItems)
```

---

## 8. 调度引擎：`WorkflowScheduler` 核心语义

AI 描述流程时需对齐以下**硬行为**：

1. **单链**：过滤 →（可选 `splitField` 多线程分片外呼，结果 merge）→ 读 `scheduleExpr` → 若有下一批 id，则 `build(nextNodes)` 再 **递归 `schedule`**。
2. **`scheduleParamSourceType`**：`ORIGINAL` 传入**最初** `businessData`；`PRE_RESPONSE` 传入**当前节点** `nodeResponse`。
3. **多首节点**（`firstFlowNodes` 多个）：每路 **异步** `processSingleNode`；单路异常 → 日志 + **空 Map**；合并规则（`mergeResults`）：
   - 同名 key 均为 **List** → **追加**合并；
   - 均为 **Map** → **递归合并**；
   - **非 List/Map** 的同名 key → **保留已有值（不覆盖）**。
4. **`splitField`**：拆成的每片 **独立** `remoteInvoke`，失败分片记日志并当空 Map，再整体 merge。

---

## 9. 远程调用与后置映射的「空结果」语义（易漏）

- 节点**未配置任何 `POST_PROCESS` 映射**时：`DefaultInterfaceRemoteInvoker.postProcess` 返回 **`{}`**，**不是**把下游原始 Map 原样透出。
- **`nodeType=NONE`**：`doInvoke` 已是 **`{}`**，若无 POST 映射，本步整体输出仍为 **`{}`**。

---

## 10. 过滤器与 `extensionParam` 键（配置化 Mock / 头）

与 **`AgentFilter` 链**相关的标准键（`ExtensionFilterConstants`）：

| 键 | 典型值 | 行为摘要 |
|----|--------|----------|
| **`filter.mock`** | 十进制 **mockId** 字符串 | `MockAgentFilter`（`@AgentActivate(order = 100)`）：查库取 Mock 原始响应，再按 `nodeType` 走 HTTP/DB JSON 或 SOAP 解析**跳过真实外呼**。**`NONE` 节点**在过滤器内解析参数时会 **不支持**（`NODE_TYPE_UNSUPPORTED`），勿对 NONE+Mock 使用此条路径。 |
| **`filter.header`** | **`true`**（忽略大小写） | `AbstractHeaderAgentFilter` 子类：为 `true` 时才从 **`metaInfo.headers`** 取出 Map 并执行子类 `doFilter` 改头，再写回 `metaInfo`。 |

控制台 **`buildWithMock`** 会向 `extensionParam` 写入 **`filter.mock`**（与手工配置等价）。


