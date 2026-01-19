---
trigger: always_on
---
# Interface-Agent 项目知识库

> **本文档说明**：Interface-Agent框架的核心概念、数据结构、配置规范和技术原理

---

## 目录

1. [框架概述](#框架概述)
2. [核心概念](#核心概念)
3. [节点配置规范](#节点配置规范)
4. [工作流配置规范](#工作流配置规范)
5. [参数映射原理](#参数映射原理)
6. [数据类型系统](#数据类型系统)
7. [映射类型详解](#映射类型详解)
8. [SpEL表达式规范](#spel表达式规范)
9. [节点扩展机制](#节点扩展机制)

---

## 框架概述

Interface-Agent是一个接口对接的可配置化中间件，用于实现上游系统与三方系统之间的参数映射和协议转换。

### 核心能力

- **协议适配**：支持HTTP、WebService、Database三种协议
- **参数映射**：支持双向参数映射（前置处理和后置处理）
- **数据转换**：支持SpEL表达式、Bean调用等复杂转换
- **嵌套处理**：支持对象嵌套、数组嵌套、多维数组
- **工作流编排**：支持多节点编排、参数过滤、数据拆分

### 数据流向

框架支持双向数据流，上下游角色根据业务场景不同而变化：

**场景一：Jarvis作为上游（档案、账单、申请单、报告查询）**
```
Jarvis(上游)  →  【前置处理】  →  三方系统HIS/PACS(下游)
                  PRE_PROCESS

三方响应      →  【后置处理】  →  Jarvis响应
                  POST_PROCESS
```

**场景二：三方系统作为上游（账单通知、报告状态通知）**
```
三方系统HIS/PACS(上游)  →  【前置处理】  →  Jarvis(下游)
                            PRE_PROCESS

Jarvis响应              →  【后置处理】  →  三方响应
                            POST_PROCESS
```

**总结**：前置处理始终是「上游入参 → 下游入参」，后置处理始终是「下游响应 → 上游响应」

---

## 核心概念

### 1. 工作流（Workflow）

工作流是对外暴露的接口入口，定义了：
- **interfaceUri**：对外接口路径
- **contentType**：入参格式（JSON/XML）
- **firstFlowNodes**：首节点列表（支持多节点编排）
- **contentMetaInfo**：XML入参解析配置

### 2. 节点（Node）

节点是调用三方接口的执行单元，包含：
- **nodeType**：节点类型（HTTP/WEBSERVICE/DATABASE）
- **metaInfo**：元数据配置（URL、请求头、SQL模板等）
- **keywordField**：关键字字段（日志追踪）
- **splitField**：拆分字段（数组批量拆分）
- **paramFilterExpr**：参数过滤表达式（条件判断）

### 3. 参数映射（ParamMapping）

参数映射是字段转换规则，分为：
- **前置处理（PRE_PROCESS）**：上游入参 → 三方入参
- **后置处理（POST_PROCESS）**：三方响应 → 上游响应

---

## 节点配置规范

### HTTP节点配置

**metaInfo结构**：
```json
{
  "url": "http://example.com/api",           // 必填
  "method": "POST",                           // 可选，默认POST
  "headers": {                                // 可选，默认{"Content-Type": "application/json"}
    "Content-Type": "application/json",
    "Authorization": "Bearer xxx"
  }
}
```

**字段说明**：
- **url**：目标接口完整URL，仅支持HTTP协议
- **method**：HTTP方法，支持GET/POST
- **headers**：静态请求头配置（动态请求头需用SPI扩展）

### WebService节点配置

**metaInfo结构**：
```json
{
  "url": "http://example.com/soap",                          // 必填
  "soupTemplate": "<soapenv:Envelope>...${inputParam}...</>", // 必填
  "method": "POST",                                           // 可选
  "headers": {"Content-Type": "text/xml;charset=UTF-8"},      // 可选
  "dataType": "json",                                         // 可选：json/xml
  "escape": false,                                            // 可选：是否转义
  "elementName": "request",                                   // 可选：通过标签名定位
  "namespace": "http://herenit.com",                          // 可选：命名空间
  "namespaceElementName": "MessageIn",                        // 可选
  "namespaceDataElementName": "pInput",                       // 可选：支持递归路径
  "isCDATA": true,                                            // 可选：默认true
  "objectTag": ["PatientInfo", "OrderInfo"]                   // 可选：对象标签
}
```

**关键概念**：
- **soupTemplate**：SOAP请求模板，`${inputParam}`作为占位符
- **elementName**：根据标签名定位业务数据（标签名唯一时使用）
- **namespace系列**：用于定位带命名空间的业务数据
- **objectTag**：指定哪些标签应解析为对象而非数组

### Database节点配置

**metaInfo结构**：
```json
{
  "driverClassName": "com.microsoft.sqlserver.jdbc.SQLServerDriver",  // 必填
  "operation": "SELECT",                                               // 必填：SELECT/INSERT/UPDATE/DELETE
  "url": "jdbc:sqlserver://10.20.4.245:1433;DatabaseName=HEMS_V5",   // 必填
  "username": "sa",                                                    // 必填
  "password": "xxx",                                                   // 必填
  "sqlTemplate": "SELECT * FROM Report WHERE patid={PatientCode}"      // 必填
}
```

**sqlTemplate占位符规则**：
- 单个参数：`{paramName}`
- 数组参数：`IN {arrayParamName}`（框架自动处理IN语句）

**查询结果**：
- 查询结果默认以`data`字段返回（数组格式）
- 数据库字段通常为小写，需映射到目标字段（通常为大写）

### 节点通用字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| nodeName | string | 是 | 节点名称（建议使用业务场景描述） |
| nodeType | string | 是 | 节点类型：HTTP/WEBSERVICE/DATABASE |
| metaInfo | string | 是 | 元数据配置（JSON字符串） |
| flowId | integer | 否 | 所属工作流ID（可先不填） |
| keywordField | string | 否 | 关键字字段（用于日志追踪） |
| splitField | string | 否 | 拆分字段（数组批量拆分） |
| paramFilterExpr | string | 否 | 参数过滤表达式（SpEL表达式） |
| remoteExtension | string | 否 | SPI扩展实现类名 |

**keywordField配置规则**：
- 默认根路径为上游入参
- 使用`.`符号访问嵌套对象字段
- 不支持数组下标访问
- **Jarvis作为上游场景**：通常使用`patientCode`作为关键字，patientCode可能在根路径或`patientInfo`对象中
- 示例：
  - `patientCode` - 根路径下的体检编号
  - `patientInfo.patientCode` - patientInfo对象中的体检编号
  - `request.header.requestId` - 多层嵌套对象的requestId字段

**splitField使用场景**：
- 三方接口不支持数组批量推送时使用
- 填写数组字段名（如`items`、`data`）
- 框架会将数组拆分为单个对象，逐个调用接口，因此需要注意在参数映射时该字段类型会从数组变为对象
- **注意**：拆分字段必须是数组类型，节点参数配置时为对象类型

**paramFilterExpr使用场景**：
1. **数组过滤**：按条件筛选数组元素
   - 示例：`(#tempItems = #data[items]?.?[departType == 'PACS']) == null || #tempItems.isEmpty() ? #data.clear() : #data.put('items', #tempItems)`
2. **条件判断**：根据字段值决定是否调用节点
   - 示例：`#data[notifyType] == 'CHARGE_SUCCESS' ? #data : #data.clear()`
3. **接口路由**：根据厂商类型分发到不同接口
   - 示例：`#data[ServiceProviderType] == 'PACS' ? #data : #data.clear()`

---

## 工作流配置规范

### 工作流字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| flowName | string | 是 | 工作流名称（建议使用业务场景描述） |
| interfaceUri | string | 是 | 对外接口路径 |
| contentType | string | 是 | 入参格式：JSON/XML |
| contentMetaInfo | string | 否 | XML入参解析配置（JSON字符串） |
| firstFlowNodes | array | 是 | 首节点ID列表 |
| status | integer | 否 | 启用状态：1启用/0禁用，默认1 |

### interfaceUri配置规范

**上游是Jarvis的场景**：使用Jarvis的BusinessMethod作为interfaceUri

| 业务类型 | interfaceUri值 |
|---------|----------------|
| 查询档案 | QueryArchives |
| 建档 | CreateArchives |
| 更新档案 | UpdateArchives |
| 挂号 | Registration |
| 账单创建 | SyncHisChagSheet |
| 账单删除 | SyncHisDelChagSheet |
| 拟退账单 | SyncHisRevokeChagSheet |
| 取消拟退 | SyncHisCancelRevokeChagSheet |
| 支付状态查询 | SyncHisQueryChagSheetStatus |
| 退费状态查询 | SyncHisQueryChagSheetRefundStatus |
| 申请单创建 | SyncPacsLisNewApply |
| 申请单取消 | SyncPacsLisCancelApply |
| 团体建档 | GroupCreateArchives |
| 团体档案更新 | GroupUpdateArchives |
| 报告获取 | GetItemResult |

**上游是三方系统的场景**：使用`/agent-open-api/**`格式
- 示例：`/agent-open-api/pacs-status`
- 示例：`/agent-open-api/lis-report`

### contentType说明

| 类型 | 说明 | 适用协议 |
|------|------|---------|
| JSON | 自动解析为key-value结构 | RESTful |
| XML | 自动解析为key-value结构，需配置contentMetaInfo | SOAP |

**XML特殊说明**：
- 不支持同名标签处理（依赖属性值区分的场景）
- 不规范的XML结构需要自定义Controller处理

### contentMetaInfo配置（仅XML）

当contentType为XML时，需要配置此字段来定位业务数据：

```json
{
  "elementName": "request",                      // 通过标签名定位
  "namespace": "http://herenit.com",            // 命名空间URI
  "namespaceElementName": "MessageIn",          // 命名空间元素名
  "namespaceDataElementName": "pInput",         // 业务数据元素名（支持递归路径）
  "isCDATA": true,                              // 业务数据是否在CDATA中，默认true
  "objectTag": ["PatientInfo", "OrderInfo"]     // 对象标签（避免被解析为数组）
}
```

### firstFlowNodes编排规则

**单节点场景**：
- 建档、创建账单、患者信息查询等单一业务场景

**多节点场景**：
- 创建申请单（同时调用多个接口）
- 报告获取（从多个系统获取报告）
- 账单状态通知（分别通知收费和退费）

**多节点编排逻辑**：
- 框架会并行处理各个节点
- 相同入参分发到各个节点
- 需要通过`paramFilterExpr`判断是否截断节点调用

---

## 参数映射原理

### 数据流向

参数映射的数据流向始终遵循以下原则（与上下游角色无关）：

```
前置处理(PRE_PROCESS):  upstream_request  →  downstream_request   (上游入参 → 下游入参)
后置处理(POST_PROCESS): downstream_response  →  upstream_response (下游响应 → 上游响应)
```

**场景一示例（上游是Jarvis）**：
```
Jarvis入参                     HIS入参
{"patientCode": "123"}  ──▶    {"patNo": "123"}
{"patientName": "张三"}         {"name": "张三"}

HIS响应                       Jarvis响应
{"returnCode": "1"}     ──▶    {"code": "200"}
{"errorMessage": "成功"}        {"message": "成功"}
```

**场景二示例（上游是HIS）**：
```
HIS通知入参                   Jarvis入参
{"orderNo": "HIS001"}   ──▶    {"billNo": "HIS001"}
{"status": "paid"}              {"payStatus": "1"}

Jarvis响应                    HIS响应
{"code": "200"}         ──▶    {"result": "success"}
{"message": "OK"}               {"msg": "OK"}
```

### 参数映射字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| nodeId | integer | 是 | 关联的节点ID |
| processType | string | 是 | PRE_PROCESS/POST_PROCESS |
| sourceParamKey | string | 条件 | 源参数名（sourceParamType非NONE时必填） |
| sourceParamType | string | 是 | 源参数类型 |
| targetParamKey | string | 是 | 目标参数名 |
| targetParamType | string | 是 | 目标参数类型 |
| mappingType | string | 是 | 映射类型 |
| mappingSource | string | 条件 | 映射来源（仅POST_PROCESS使用） |
| mappingRule | string | 否 | 映射规则（CONSTANT/EXPRESSION时使用） |
| paramDesc | string | 否 | 参数描述 |
| sort | integer | 否 | 排序，默认0 |
| parentId | integer | 否 | 父参数ID（构建嵌套结构） |
| keepSingleElementKey | boolean | 否 | 保留单元素键（XML数组处理） |

---

## 数据类型系统

### 支持的数据类型

| 类型 | 说明 | 使用场景 |
|------|------|---------|
| STRING | 字符串 | 普通文本字段 |
| INTEGER | 整数 | 数值字段 |
| LONG | 长整型 | 大数值字段 |
| DOUBLE | 双精度浮点型 | 小数字段 |
| BOOLEAN | 布尔值 | 开关字段 |
| OBJECT | 对象 | 需要配置子参数 |
| ARRAY | 数组 | 需要配置子参数（必须包含虚拟节点） |
| PURE_ARRAY | 纯数组 | 数组元素为基本类型 |
| NONE | 无类型 | 固定值、表达式、虚拟节点 |

### 嵌套结构处理

**对象嵌套**：
- 父节点：`targetParamType=OBJECT`
- 子节点：通过`parentId`关联父节点的`configId`

**数组嵌套**：
- 数组节点：`targetParamType=ARRAY`
- 虚拟节点：`sourceParamType=NONE`，`targetParamType=OBJECT`，`targetParamKey`通常以`Node`结尾
- 元素字段：作为虚拟节点的子参数

**虚拟节点规则**：
- 数组类型**必须**包含虚拟节点
- 虚拟节点不映射实际数据，仅用于结构描述
- 虚拟节点命名规范：`itemNode`、`orderNode`、`dataNode`

---

## 映射类型详解

### 1. NAME（值映射）

直接按名称取值，将sourceParamKey的值直接映射到targetParamKey。

**使用场景**：
- 字段名相同且类型一致
- 字段名不同但含义相同

**配置示例**：
```json
{
  "processType": "PRE_PROCESS",
  "sourceParamKey": "patientCode",
  "sourceParamType": "STRING",
  "targetParamKey": "patNo",
  "targetParamType": "STRING",
  "mappingType": "NAME",
  "mappingSource": null,
  "mappingRule": null
}
```

### 2. CONSTANT（固定值映射）

使用固定值，不依赖源参数。

**使用场景**：
- 系统标识、固定状态值、固定配置

**配置示例**：
```json
{
  "processType": "PRE_PROCESS",
  "sourceParamKey": null,
  "sourceParamType": "NONE",
  "targetParamKey": "systemId",
  "targetParamType": "STRING",
  "mappingType": "CONSTANT",
  "mappingSource": null,
  "mappingRule": "JARVIS_001"
}
```

### 3. EXPRESSION（SpEL表达式映射）

使用SpEL表达式进行复杂转换。

**使用场景**：
- 条件判断（性别转换、状态码转换）
- 字符串处理
- 类型转换
- 从对象中提取子字段

**配置示例**：
```json
{
  "processType": "PRE_PROCESS",
  "sourceParamKey": "sex",
  "sourceParamType": "STRING",
  "targetParamKey": "gender",
  "targetParamType": "STRING",
  "mappingType": "EXPRESSION",
  "mappingSource": null,
  "mappingRule": "#data == '1' ? '男' : '女'"
}
```

### 4. BEAN_EXPRESSION（Bean表达式映射）

调用Spring Bean方法进行转换。

**使用场景**：
- 复杂业务逻辑处理
- 数据库查询关联

**内置Bean方法**：
- `#hemsMapper.selectPatientCodeByVisitNo(visitNo)`：根据就诊号查询体检编号
- `#hemsMapper.selectPatientCodeByApplyId(applyId)`：根据申请单ID查询体检编号

**配置示例**：
```json
{
  "processType": "POST_PROCESS",
  "sourceParamKey": "visitNo",
  "sourceParamType": "STRING",
  "targetParamKey": "patientCode",
  "targetParamType": "STRING",
  "mappingType": "BEAN_EXPRESSION",
  "mappingSource": "RESPONSE",
  "mappingRule": "#hemsMapper.selectPatientCodeByVisitNo(#data)"
}
```

### 5. SINGLE_MAP（单值映射）

用于数组元素为基本类型的特殊场景。

**使用场景**：
- 虚拟节点的父节点targetParamType为数组
- 数组元素都是基础数据类型时
- 子节点需要设置为SINGLE_MAP

**注意**：一般场景使用NAME或EXPRESSION即可满足需求

---

## SpEL表达式规范

### 上下文变量

| 处理类型 | 变量名 | 说明 |
|---------|--------|------|
| PRE_PROCESS | `#data` | 当前层级的映射源数据 |
| PRE_PROCESS | `#parent` | 父层级的映射源数据 |
| PRE_PROCESS | `#source` | 根层级的映射源数据 |
| POST_PROCESS | `#data` | 当前层级的映射源数据 |
| POST_PROCESS | `#input` | 原始上游入参 |
| POST_PROCESS | `#response` | 下游接口响应 |

### 常用表达式示例

**条件判断**：
```spel
#data == '1' ? '男' : '女'
#data[sex] == '1' ? '男' : '女'
#response[returnCode] == '1' ? '200' : '-1'
```

**从对象提取字段**：
```spel
#data[hisPatientId]
#data[sex] == '1' ? '男' : '女'
```

**从根级响应取值**：
```spel
#response[returnCode] == '1' ? '200' : '-1'
#input[patientCode]
```

**数组过滤**：
```spel
(#tempItems = #data[items]?.?[departType == 'PACS']) == null || #tempItems.isEmpty() ? #data.clear() : #data.put('items', #tempItems)
```

**条件路由**：
```spel
#data[notifyType] == 'CHARGE_SUCCESS' ? #data : #data.clear()
#data[ServiceProviderType] == 'PACS' ? #data : #data.clear()
```

---

## 节点扩展机制

### 扩展场景

建议使用节点扩展的场景：
1. **动态请求头**：请求头信息需要动态计算（动态签名、动态token）
2. **非规范协议**：三方接口不遵循标准HTTP/WebService协议
3. **复杂参数处理**：参数转换逻辑过于复杂，SpEL表达式难以实现
4. **自定义调用逻辑**：需要特殊的重试、熔断、降级等逻辑
5. **特殊数据格式**：三方接口使用特殊的数据格式（自定义加密）

### 配置方式

节点的`remoteExtension`字段用于指定SPI扩展的实现类名称。

**节点配置示例**：
```json
{
  "nodeName": "特殊厂商接口节点",
  "nodeType": "HTTP",
  "metaInfo": "{\"url\":\"http://example.com/api\"}",
  "remoteExtension": "customVendorExtension"
}
```

### SPI扩展实现

扩展实现需要：
1. 实现框架提供的SPI接口
2. 在`META-INF/dubbo`目录下配置扩展点
3. 通过节点的`remoteExtension`字段关联扩展实现

**扩展点配置文件示例**：`META-INF/dubbo/com.helianhealth.agent.invocation.remote.RemoteClient`
```properties
customVendorExtension=com.example.extension.CustomVendorExtension
```

### 扩展使用注意事项

1. **优先使用标准配置**：只有在标准配置无法满足需求时才使用扩展
2. **扩展隔离**：不同厂商的扩展应该独立实现，避免耦合
3. **错误处理**：扩展中要做好异常处理和日志记录
4. **性能考虑**：扩展代码要注意性能优化，避免阻塞主流程
5. **文档说明**：扩展实现要有清晰的文档说明，便于维护

---

## mappingSource详解

### 使用时机

`mappingSource`**仅在后置处理（POST_PROCESS）时使用**。

### 可选值

| 值 | 说明 | 使用场景 |
|----|------|---------|
| null | 前置处理时使用 | PRE_PROCESS |
| RESPONSE | 从三方接口响应中取值 | 三方响应包含所需数据 |
| INPUT | 从原始上游入参中取值 | 三方响应不完整，需要补充入参数据 |

### 核心场景：同时使用INPUT和RESPONSE

后置处理时，有时需要同时从下游响应(RESPONSE)和原始入参(INPUT)中取值组合成新的响应。

**场景示例**：拼凑对象（从两个数据源取字段组合成一个对象）

下游响应返回HIS患者ID：
```json
{"returnCode": "1", "hisPatientId": "HIS123456"}
```

上游原始入参包含体检编号：
```json
{"patientCode": "P001", "patientName": "张三"}
```

上游要求的响应需要同时包含两边的数据：
```json
{"code": "200", "rsp": {"patientCode": "P001", "hisPatientId": "HIS123456"}}
```

**配置方案**：通过EXPRESSION使用根级变量
```json
[
  {
    "processType": "POST_PROCESS",
    "sourceParamType": "NONE",
    "targetParamKey": "rsp",
    "targetParamType": "OBJECT",
    "mappingType": "NAME"
  },
  {
    "processType": "POST_PROCESS",
    "sourceParamKey": "patientCode",
    "sourceParamType": "STRING",
    "targetParamKey": "patientCode",
    "targetParamType": "STRING",
    "mappingType": "NAME",
    "mappingSource": "INPUT",
    "paramDesc": "从入参取体检编号"
  },
  {
    "processType": "POST_PROCESS",
    "sourceParamKey": "hisPatientId",
    "sourceParamType": "STRING",
    "targetParamKey": "hisPatientId",
    "targetParamType": "STRING",
    "mappingType": "NAME",
    "mappingSource": "RESPONSE",
    "paramDesc": "从响应取HIS患者ID"
  }
]
```

**配置规则与限制**：

1. **根级变量可用**：`#response` 和 `#input` 作为根节点变量始终可用，可以通过表达式直接访问

2. **数据源继承限制**：`#data` 会根据当前节点的 `mappingSource` 往里面嵌套映射
   - 如果父节点确定了 `mappingSource`，子节点只能在该数据源下映射
   - **数组中无法同时依赖2个数据源**：同一数组的子元素不能混用INPUT和RESPONSE

3. **多元素数组拼凑不支持**：如果需要从 INPUT 和 RESPONSE 两边的数组数据拼凑成新数组，框架本身无法实现
   - ✅ 可以：数组只有1个元素，用表达式取`[0]`
   - ❌ 不行：数组有多个元素，需要按索引一一对应拼凑
   - **解决方案**：需要通过SPI扩展自定义处理逻辑

4. **单元素数组场景**：当确定数组只有1个元素时，可以用表达式取固定下标
   ```json
   {
     "processType": "POST_PROCESS",
     "sourceParamKey": null,
     "sourceParamType": "NONE",
     "targetParamKey": "applyId",
     "targetParamType": "STRING",
     "mappingType": "EXPRESSION",
     "mappingSource": "INPUT",
     "mappingRule": "#response[data][0][medTechRequestId]"
   }
   ```
   ❗ **前提条件**：必须保证数组大小为1，否则只能取到第一个元素

---

## 配置注意事项

### 必填项校验

- `processType`、`targetParamKey`、`targetParamType`、`mappingType` 必填
- `sourceParamType`不为`NONE`时，`sourceParamKey`必填

### 数组虚拟节点

- 数组映射**必须**包含虚拟节点
- 虚拟节点的`targetParamKey`通常以`Node`结尾（如`itemNode`、`orderNode`）

### 枚举值校验

- 所有枚举类型字段使用字符串格式
- 接口会自动校验并返回清晰错误提示

### 嵌套结构

- 通过`childParams`字段递归处理
- 无需手动维护`parentId`

### 表达式映射

- 前置处理使用`#data`变量
- 后置处理使用`#response`、`#input`变量

### 导入前清空

- 建议设置`clearBeforeImport: true`避免配置冲突

### 配置顺序

- 必须先创建父参数，再创建子参数
- 因为子参数需要引用父参数的`configId`

### 类型匹配

- `sourceParamType`和`targetParamType`需要合理匹配
- 避免类型转换错误

### 排序控制

- 通过`sort`字段控制同级参数的处理顺序
- 默认为0
