# AI操作技能手册

> **本文档说明**：MCP接口调用方法和配置操作的具体步骤

---

## 目录

1. [MCP接口总览](#mcp接口总览)
2. [节点操作](#节点操作)
3. [参数映射操作](#参数映射操作)
4. [工作流操作](#工作流操作)
5. [配置模板](#配置模板)
6. [完整对接示例](#完整对接示例)

---

## MCP接口总览

### 基础路径

| 模块 | 基础路径 |
|------|---------|
| 节点管理 | `/v1/ai/mcp/workflow-node` |
| 工作流管理 | `/v1/ai/mcp/workflow` |
| 参数映射管理 | `/v1/ai/mcp/node-param-config` |

### 接口清单

| 操作 | 节点 | 工作流 | 参数映射 |
|------|------|--------|---------|
| 查询列表 | GET /list | GET /list | GET /list?nodeId=x |
| 查询单个 | GET /get/{id} | GET /get/{id} | GET /export/{nodeId} |
| 创建 | POST /create | POST /create | POST /import |
| 更新 | POST /update | POST /update | POST /update |
| 删除 | DELETE /delete/{id} | DELETE /delete/{id} | DELETE /delete/{id} |
| 全量删除 | - | - | DELETE /delete-all/{nodeId} |

---

## 节点操作

### 创建HTTP节点

**接口**：`POST /v1/ai/mcp/workflow-node/create`

**请求体**：
```json
{
  "nodeName": "HIS建档接口",
  "nodeType": "HTTP",
  "metaInfo": "{\"url\":\"http://his.example.com/api/createPatient\"}",
  "keywordField": "patientCode"
}
```

**字段说明**：

| 字段 | 必填 | 说明 |
|------|------|------|
| nodeName | 是 | 节点名称（建议使用业务描述） |
| nodeType | 是 | HTTP / WEBSERVICE / DATABASE |
| metaInfo | 是 | 元数据配置（JSON字符串） |
| keywordField | 否 | 关键字字段（用于日志追踪） |
| splitField | 否 | 拆分字段（数组批量拆分） |
| paramFilterExpr | 否 | 参数过滤表达式 |

**响应**：
```json
{
  "code": "200",
  "message": "OK",
  "rsp": 8
}
```
> 返回的 `rsp` 就是 nodeId，后续配置参数映射时使用

### 创建WebService节点

**请求体**：
```json
{
  "nodeName": "PACS申请单接口",
  "nodeType": "WEBSERVICE",
  "metaInfo": "{\"url\":\"http://pacs.example.com/soap\",\"soupTemplate\":\"<soapenv:Envelope>...<![CDATA[${inputParam}]]>...</soapenv:Envelope>\",\"elementName\":\"request\"}",
  "keywordField": "patientCode"
}
```

### 创建Database节点

**请求体**：
```json
{
  "nodeName": "查询报告数据",
  "nodeType": "DATABASE",
  "metaInfo": "{\"driverClassName\":\"com.microsoft.sqlserver.jdbc.SQLServerDriver\",\"url\":\"jdbc:sqlserver://10.20.4.245:1433;DatabaseName=HEMS\",\"username\":\"sa\",\"password\":\"xxx\",\"operation\":\"SELECT\",\"sqlTemplate\":\"SELECT * FROM Report WHERE patid={PatientCode}\"}"
}
```

### 查询所有节点

**接口**：`GET /v1/ai/mcp/workflow-node/list`

**响应**：
```json
{
  "code": "200",
  "rsp": [
    {
      "nodeId": 7,
      "nodeName": "HIS建档接口",
      "nodeType": "HTTP",
      "metaInfo": "{\"url\":\"http://his.example.com/api/createPatient\"}",
      "keywordField": "patientCode"
    }
  ]
}
```

### 查询单个节点

**接口**：`GET /v1/ai/mcp/workflow-node/get/{nodeId}`

### 更新节点

**接口**：`POST /v1/ai/mcp/workflow-node/update`

**请求体**：同创建，需包含 `nodeId` 字段

### 删除节点

**接口**：`DELETE /v1/ai/mcp/workflow-node/delete/{nodeId}`

> ⚠️ 删除节点会同时删除关联的参数配置

---

## 参数映射操作

### 导入参数映射（推荐）

**接口**：`POST /v1/ai/mcp/node-param-config/import`

**请求体**：
```json
{
  "nodeId": 8,
  "clearBeforeImport": true,
  "params": [
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "patientCode",
      "sourceParamType": "STRING",
      "targetParamKey": "patNo",
      "targetParamType": "STRING",
      "mappingType": "NAME",
      "paramDesc": "患者编号"
    },
    {
      "processType": "POST_PROCESS",
      "sourceParamKey": null,
      "sourceParamType": "NONE",
      "targetParamKey": "code",
      "targetParamType": "STRING",
      "mappingType": "EXPRESSION",
      "mappingSource": "RESPONSE",
      "mappingRule": "#response['returnCode'] == '1' ? '200' : '-1'",
      "paramDesc": "状态码转换"
    }
  ]
}
```

**字段说明**：

| 字段 | 必填 | 说明 |
|------|------|------|
| nodeId | 是 | 关联的节点ID |
| clearBeforeImport | 否 | 是否先清空（默认true） |
| processType | 是 | PRE_PROCESS / POST_PROCESS |
| sourceParamKey | 条件 | 源参数名（sourceParamType非NONE时必填） |
| sourceParamType | 是 | STRING/INTEGER/OBJECT/ARRAY/NONE等 |
| targetParamKey | 是 | 目标参数名 |
| targetParamType | 是 | 同sourceParamType |
| mappingType | 是 | NAME/CONSTANT/EXPRESSION/BEAN_EXPRESSION |
| mappingSource | 条件 | 后置处理时使用：RESPONSE/INPUT |
| mappingRule | 条件 | CONSTANT/EXPRESSION时使用 |
| paramDesc | 否 | 参数描述 |
| childParams | 否 | 子参数（嵌套结构） |

### 嵌套对象映射示例

```json
{
  "nodeId": 8,
  "params": [
    {
      "processType": "PRE_PROCESS",
      "sourceParamType": "NONE",
      "targetParamKey": "patientInfo",
      "targetParamType": "OBJECT",
      "mappingType": "NAME",
      "paramDesc": "患者信息对象",
      "childParams": [
        {
          "processType": "PRE_PROCESS",
          "sourceParamKey": "patientCode",
          "sourceParamType": "STRING",
          "targetParamKey": "code",
          "targetParamType": "STRING",
          "mappingType": "NAME"
        },
        {
          "processType": "PRE_PROCESS",
          "sourceParamKey": "patientName",
          "sourceParamType": "STRING",
          "targetParamKey": "name",
          "targetParamType": "STRING",
          "mappingType": "NAME"
        }
      ]
    }
  ]
}
```

### 数组映射示例（必须包含虚拟节点）

```json
{
  "nodeId": 8,
  "params": [
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "items",
      "sourceParamType": "ARRAY",
      "targetParamKey": "orders",
      "targetParamType": "ARRAY",
      "mappingType": "NAME",
      "paramDesc": "订单数组",
      "childParams": [
        {
          "processType": "PRE_PROCESS",
          "sourceParamType": "NONE",
          "targetParamKey": "orderNode",
          "targetParamType": "OBJECT",
          "mappingType": "NAME",
          "paramDesc": "虚拟节点",
          "childParams": [
            {
              "processType": "PRE_PROCESS",
              "sourceParamKey": "itemCode",
              "sourceParamType": "STRING",
              "targetParamKey": "code",
              "targetParamType": "STRING",
              "mappingType": "NAME"
            },
            {
              "processType": "PRE_PROCESS",
              "sourceParamKey": "itemName",
              "sourceParamType": "STRING",
              "targetParamKey": "name",
              "targetParamType": "STRING",
              "mappingType": "NAME"
            }
          ]
        }
      ]
    }
  ]
}
```

### 导出参数映射（树形结构）

**接口**：`GET /v1/ai/mcp/node-param-config/export/{nodeId}`

**响应**：
```json
{
  "code": "200",
  "rsp": {
    "nodeId": 8,
    "nodeName": "HIS建档接口",
    "preProcessParams": [...],
    "postProcessParams": [...],
    "totalCount": 10
  }
}
```

### 查询参数列表（扁平化）

**接口**：`GET /v1/ai/mcp/node-param-config/list`

**请求参数**：
- `nodeId` (必填): 节点ID
- `processType` (可选): PRE_PROCESS / POST_PROCESS

### 全量删除参数配置

**接口**：`DELETE /v1/ai/mcp/node-param-config/delete-all/{nodeId}`

### 更新单个参数

**接口**：`POST /v1/ai/mcp/node-param-config/update`

**请求体**：需包含 `configId` 字段

### 删除单个参数

**接口**：`DELETE /v1/ai/mcp/node-param-config/delete/{configId}`

---

## 工作流操作

### 创建工作流

**接口**：`POST /v1/ai/mcp/workflow/create`

**请求体**：
```json
{
  "flowName": "个人建档接口",
  "interfaceUri": "CreateArchives",
  "contentType": "JSON",
  "firstFlowNodes": [8],
  "status": 1
}
```

**字段说明**：

| 字段 | 必填 | 说明 |
|------|------|------|
| flowName | 是 | 工作流名称 |
| interfaceUri | 是 | 接口URI（BusinessMethod或/agent-open-api/**） |
| contentType | 是 | JSON / XML |
| firstFlowNodes | 是 | 首节点ID列表 |
| contentMetaInfo | 否 | XML入参解析配置 |
| status | 否 | 1启用/0禁用（默认1） |

**响应**：
```json
{
  "code": "200",
  "rsp": 10
}
```
> 返回的 `rsp` 就是 flowId

### 查询所有工作流

**接口**：`GET /v1/ai/mcp/workflow/list`

**响应**：
```json
{
  "code": "200",
  "rsp": [
    {
      "flowId": 10,
      "flowName": "个人建档接口",
      "interfaceUri": "CreateArchives",
      "contentType": "JSON",
      "firstFlowNodes": [8],
      "status": 1
    }
  ]
}
```

### 查询单个工作流

**接口**：`GET /v1/ai/mcp/workflow/get/{flowId}`

### 更新工作流

**接口**：`POST /v1/ai/mcp/workflow/update`

**请求体**：同创建，需包含 `flowId` 字段

### 删除工作流

**接口**：`DELETE /v1/ai/mcp/workflow/delete/{flowId}`

---

## 配置模板

### HTTP节点metaInfo模板

**最简配置**（仅URL，其他使用默认值）：
```json
{"url": "http://example.com/api"}
```

**完整配置**：
```json
{
  "url": "http://example.com/api",
  "method": "POST",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer xxx"
  }
}
```

### WebService节点metaInfo模板

**标准配置**：
```json
{
  "url": "http://example.com/soap",
  "soupTemplate": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n  <soapenv:Body>\n    <request><![CDATA[${inputParam}]]></request>\n  </soapenv:Body>\n</soapenv:Envelope>",
  "elementName": "response"
}
```

**带命名空间配置**：
```json
{
  "url": "http://example.com/soap",
  "soupTemplate": "...",
  "namespace": "http://herenit.com",
  "namespaceElementName": "MessageIn",
  "namespaceDataElementName": "pInput",
  "isCDATA": true,
  "objectTag": ["PatientInfo", "OrderInfo"]
}
```

### Database节点metaInfo模板

```json
{
  "driverClassName": "com.microsoft.sqlserver.jdbc.SQLServerDriver",
  "url": "jdbc:sqlserver://10.20.4.245:1433;DatabaseName=HEMS",
  "username": "sa",
  "password": "xxx",
  "operation": "SELECT",
  "sqlTemplate": "SELECT * FROM Report WHERE patid={PatientCode}"
}
```

**驱动类名参考**：
| 数据库 | driverClassName |
|-------|-----------------|
| SQL Server | com.microsoft.sqlserver.jdbc.SQLServerDriver |
| MySQL | com.mysql.cj.jdbc.Driver |
| OpenGauss | org.opengauss.Driver |

### 前置处理参数模板

**NAME映射**（字段名不同）：
```json
{
  "processType": "PRE_PROCESS",
  "sourceParamKey": "patientCode",
  "sourceParamType": "STRING",
  "targetParamKey": "patNo",
  "targetParamType": "STRING",
  "mappingType": "NAME",
  "paramDesc": "患者编号"
}
```

**EXPRESSION映射**（需要转换）：
```json
{
  "processType": "PRE_PROCESS",
  "sourceParamKey": "sex",
  "sourceParamType": "STRING",
  "targetParamKey": "gender",
  "targetParamType": "STRING",
  "mappingType": "EXPRESSION",
  "mappingRule": "#data == '1' ? '男' : '女'",
  "paramDesc": "性别转换"
}
```

**CONSTANT映射**（固定值）：
```json
{
  "processType": "PRE_PROCESS",
  "sourceParamKey": null,
  "sourceParamType": "NONE",
  "targetParamKey": "source",
  "targetParamType": "STRING",
  "mappingType": "CONSTANT",
  "mappingRule": "JARVIS",
  "paramDesc": "数据来源标识"
}
```

### 后置处理参数模板

**状态码转换**：
```json
{
  "processType": "POST_PROCESS",
  "sourceParamKey": null,
  "sourceParamType": "NONE",
  "targetParamKey": "code",
  "targetParamType": "STRING",
  "mappingType": "EXPRESSION",
  "mappingSource": "RESPONSE",
  "mappingRule": "#response['returnCode'] == '1' ? '200' : '-1'",
  "paramDesc": "状态码转换"
}
```

**响应消息映射**：
```json
{
  "processType": "POST_PROCESS",
  "sourceParamKey": "errorMessage",
  "sourceParamType": "STRING",
  "targetParamKey": "message",
  "targetParamType": "STRING",
  "mappingType": "NAME",
  "mappingSource": "RESPONSE",
  "paramDesc": "响应消息"
}
```

**rsp对象包装**：
```json
{
  "processType": "POST_PROCESS",
  "sourceParamType": "NONE",
  "targetParamKey": "rsp",
  "targetParamType": "OBJECT",
  "mappingType": "NAME",
  "paramDesc": "响应数据对象",
  "childParams": [
    {
      "processType": "POST_PROCESS",
      "sourceParamKey": "hisPatientId",
      "sourceParamType": "STRING",
      "targetParamKey": "hisPatientId",
      "targetParamType": "STRING",
      "mappingType": "NAME",
      "mappingSource": "RESPONSE"
    }
  ]
}
```

---

## 完整对接示例

### 对接HIS建档接口（完整流程）

#### 步骤1：创建节点

```bash
POST /v1/ai/mcp/workflow-node/create
Content-Type: application/json

{
  "nodeName": "HIS建档接口",
  "nodeType": "HTTP",
  "metaInfo": "{\"url\":\"http://his.example.com/api/createPatient\",\"method\":\"POST\"}",
  "keywordField": "patientCode"
}
```

**响应**：`{"code":"200","rsp":8}`

记录 nodeId = 8

#### 步骤2：导入参数映射

```bash
POST /v1/ai/mcp/node-param-config/import
Content-Type: application/json

{
  "nodeId": 8,
  "clearBeforeImport": true,
  "params": [
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "patientCode",
      "sourceParamType": "STRING",
      "targetParamKey": "patNo",
      "targetParamType": "STRING",
      "mappingType": "NAME",
      "paramDesc": "患者编号"
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "patientName",
      "sourceParamType": "STRING",
      "targetParamKey": "name",
      "targetParamType": "STRING",
      "mappingType": "NAME",
      "paramDesc": "患者姓名"
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "sex",
      "sourceParamType": "STRING",
      "targetParamKey": "gender",
      "targetParamType": "STRING",
      "mappingType": "EXPRESSION",
      "mappingRule": "#data == '1' ? '男' : '女'",
      "paramDesc": "性别转换"
    },
    {
      "processType": "POST_PROCESS",
      "sourceParamKey": null,
      "sourceParamType": "NONE",
      "targetParamKey": "code",
      "targetParamType": "STRING",
      "mappingType": "EXPRESSION",
      "mappingSource": "RESPONSE",
      "mappingRule": "#response['returnCode'] == '1' ? '200' : '-1'",
      "paramDesc": "状态码转换"
    },
    {
      "processType": "POST_PROCESS",
      "sourceParamKey": "errorMessage",
      "sourceParamType": "STRING",
      "targetParamKey": "message",
      "targetParamType": "STRING",
      "mappingType": "NAME",
      "mappingSource": "RESPONSE",
      "paramDesc": "响应消息"
    },
    {
      "processType": "POST_PROCESS",
      "sourceParamType": "NONE",
      "targetParamKey": "rsp",
      "targetParamType": "OBJECT",
      "mappingType": "NAME",
      "paramDesc": "响应数据对象",
      "childParams": [
        {
          "processType": "POST_PROCESS",
          "sourceParamKey": "hisPatientId",
          "sourceParamType": "STRING",
          "targetParamKey": "hisPatientId",
          "targetParamType": "STRING",
          "mappingType": "NAME",
          "mappingSource": "RESPONSE"
        }
      ]
    }
  ]
}
```

#### 步骤3：创建工作流

```bash
POST /v1/ai/mcp/workflow/create
Content-Type: application/json

{
  "flowName": "个人建档接口",
  "interfaceUri": "CreateArchives",
  "contentType": "JSON",
  "firstFlowNodes": [8],
  "status": 1
}
```

**响应**：`{"code":"200","rsp":10}`

#### 步骤4：验证配置

```bash
GET /v1/ai/mcp/node-param-config/export/8
```

检查返回的配置是否正确。

---

## 操作建议

### 推荐操作顺序

1. **创建节点** → 获取 nodeId
2. **导入参数映射** → 使用 nodeId
3. **创建工作流** → 关联 nodeId

### 常用技巧

1. **批量导入**：优先使用 `/import` 接口，支持一次性导入所有配置
2. **清空重导**：设置 `clearBeforeImport: true`，避免配置冲突
3. **导出验证**：导入后用 `/export` 接口验证配置正确性
4. **嵌套配置**：使用 `childParams` 递归配置，无需手动维护 parentId

### 错误处理

接口返回的错误信息格式：
```json
{
  "code": "-1",
  "message": "参数校验失败: mappingType 'DIRECT' 无效，可选值: [CONSTANT, NAME, EXPRESSION, BEAN_EXPRESSION, SINGLE_MAP]"
}
```

根据 message 提示修正参数即可。
