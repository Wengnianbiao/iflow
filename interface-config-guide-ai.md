# 接口配置框架参考文档

本文档用于指导AI如何根据业务需求生成完整的接口配置，包括工作流、节点和参数映射配置。

## 目录

1. [节点配置](#节点配置)
2. [工作流配置](#工作流配置)
3. [参数映射配置](#参数映射配置)
4. [配置示例](#配置示例)

---

## 节点配置

### 节点类型

框架支持三种节点类型：
- **HTTP**: 用于调用RESTful接口
- **WEBSERVICE**: 用于调用SOAP协议接口
- **DATABASE**: 用于执行数据库查询

### HTTP节点配置

#### 元数据配置（metaInfo）

HTTP节点的元数据配置为JSON格式，包含以下字段：

**必填字段：**
- `url` (string, 必填): 目标接口的完整URL地址，仅支持HTTP协议
  - 示例: `http://10.192.50.204:8088/PACS/HssExmeReceive`
  - 示例: `http://gapi-c2f.seenew.info/hbos-test/open-api/gw-api/hbos-business-thirdparty-integration/common/rhc/createResidentOrgRegistInfo`

**可选字段：**
- `method` (string, 可选): HTTP请求方法，缺省值为`POST`，支持`GET`、`POST`方法
  - 缺省值: `POST`（适用于大多数接口）
  - 可选值: `GET`（适用于查询类接口）
- `headers` (object, 可选): JSON对象格式的HTTP请求头信息，用于设置内容类型、认证信息等
  - 缺省值: `{"Content-Type": "application/json"}`
  - 示例（包含认证）: `{"Content-Type": "application/json", "App-Key": "mvTLtFJGmectBac8unwIf1bF"}`

**注意事项：**
- 框架仅支持静态的请求头信息配置，对于动态的请求头接口请使用SPI扩展对接

**配置示例：**

缺省值版本（仅url，method和headers使用默认值）：
```json
{
  "url": "http://10.192.50.204:8088/PACS/HssExmeReceive"
}
```

完整版本（包含所有字段和认证信息）：
```json
{
  "url": "http://gapi-c2f.seenew.info/hbos-test/open-api/gw-api/hbos-business-thirdparty-integration/common/rhc/createResidentOrgRegistInfo",
  "method": "POST",
  "headers": {
    "Content-Type": "application/json",
    "App-Key": "mvTLtFJGmectBac8unwIf1bF"
  }
}
```

#### 其他节点字段

- `nodeName` (string, 必填): 节点名称
- `nodeType` (string, 必填): 节点类型，值为`HTTP`
- `flowId` (integer, 可选): 关联的工作流ID，创建节点时可先不填，后续关联
- `keywordField` (string, 可选): 用于日志记录和数据追踪的关键标识字段名
  - 默认根路径为上游入参
  - 使用`.`符号访问嵌套对象的字段，如：`patientCode`、`patientInfo.patientCode`、`request.header.requestId`
  - 不支持数组下标访问，只能配置对象字段路径
- `splitField` (string, 可选): 拆分字段，当三方接口不支持数组类型批量推送数据时使用
  - 必须是数组类型，框架会根据数组大小自动调用相应次数的接口
  - 使用场景：
    - 申请单发送场景：拆分字段填写`items`，框架会将items数组拆分为对象后调用接口
    - 数据上报场景：拆分字段填写`data`，对数据库查询返回的data数组进行拆分上报
- `paramFilterExpr` (string, 可选): 参数过滤表达式，使用SpEL表达式过滤业务数据
  - 只有符合条件的数据才会调用该节点
  - 示例（数组过滤）: `(#tempItems = #data['items']?.?[departType == 'PACS']) == null || #tempItems.isEmpty() ? #data.clear() : #data.put('items', #tempItems)`
  - 示例（条件判断）: `#data['notifyType'] == 'CHARGE_SUCCESS' ? #data : #data.clear()`
  - 示例（厂商类型分发）: `#data['ServiceProviderType'] == 'PACS' ? #data : #data.clear()`

---

### WebService节点配置

#### 元数据配置（metaInfo）

WebService节点的元数据配置为JSON格式，包含以下字段：

**注意事项：**
- 框架只处理较为规范的SOAP协议，对于完全不遵守SOAP协议规范去封装请求和响应的厂商接口建议通过SPI拓展对接

#### 请求配置字段

**必填字段：**
- `url` (string, 必填): WebService接口的完整URL地址，用于发送SOAP请求
  - 示例: `http://10.20.0.141:8805/soup-api/pacs_apply`
- `soupTemplate` (string, 必填): SOAP请求模板，使用`${inputParam}`作为占位符
  - 框架会将节点参数配置转换后的业务数据填充到该位置，生成完整的SOAP请求消息
  - 示例: `<soapenv:Envelope>...<her:pInput><![CDATA[${inputParam}]]></her:pInput>...</soapenv:Envelope>`

**可选字段：**
- `method` (string, 可选): HTTP请求方法，缺省值为`POST`
- `headers` (object, 可选): HTTP请求头，JSON对象格式，缺省值为`{"Content-Type": "text/xml;charset=UTF-8"}`
- `dataType` (string, 可选): 业务数据格式，值为`json`或`xml`，用于标识请求中业务数据的格式类型
  - 当业务数据在CDATA中且为JSON格式时需设置为`json`

#### 响应配置字段

针对响应，我们只关心业务数据，以下配置用于从SOAP响应中定位和解析业务数据：

- `escape` (boolean, 可选): 是否使用转义符替换字符串中的特殊字符，用于批量过滤转义字符
- `elementName` (string, 可选): 根据标签名称定位到业务数据，当响应中业务数据所在的标签名唯一时使用
  - 示例: `request`（通过request标签定位业务数据）
- `namespace` (string, 可选): 命名空间URI，与`namespaceElementName`和`namespaceDataElementName`组合使用
  - 示例: `http://herenit.com`
- `namespaceElementName` (string, 可选): 命名空间下的元素名，与`namespace`组合使用
  - 示例: `MessageIn`
- `namespaceDataElementName` (string, 可选): 业务数据元素名，支持递归路径（使用.分隔）
  - 示例: `pInput`（直接元素名）
  - 示例: `param.request`（递归路径）
- `isCDATA` (boolean, 可选): 业务数据是否为CDATA格式，缺省值为`true`
  - 在解析业务数据时，用于标识三方接口响应是否将业务数据包裹在CDATA标签中
- `objectTag` (array, 可选): 用于标识哪些标签是对象结构，数组格式
  - 当配置了该字段后，指定的标签会被解析为对象而非数组
  - 对于嵌套的标签，如果不配置该字段，框架会按照数组解析
  - 示例: `["PatientInfo", "OrderInfo"]`

**配置示例：**

缺省值版本（仅必填字段，method和headers使用默认值且三方响应为业务数据XML不包含soap信封）：
```json
{
  "url": "http://10.20.0.141:8805/soup-api/pacs_apply",
  "soupTemplate": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:her=\"http://herenit.com\">\n  <soapenv:Header></soapenv:Header>\n  <soapenv:Body>\n    <her:MessageIn>\n      <her:DataType>XML</her:DataType>\n      <her:pInput><![CDATA[${inputParam}]]></her:pInput>\n    </her:MessageIn>\n  </soapenv:Body>\n</soapenv:Envelope>"
}
```

常规版本（包含url、soupTemplate、elementName）：
```json
{
  "url": "http://10.20.0.141:8805/soup-api/pacs_apply",
  "soupTemplate": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:her=\"http://herenit.com\">\n  <soapenv:Header></soapenv:Header>\n  <soapenv:Body>\n    <her:MessageIn>\n      <her:DataType>XML</her:DataType>\n      <her:pInput><![CDATA[${inputParam}]]></her:pInput>\n    </her:MessageIn>\n  </soapenv:Body>\n</soapenv:Envelope>",
  "elementName": "request"
}
```

完整版本（包含所有字段和响应配置）：
```json
{
  "url": "http://10.20.0.141:8805/soup-api/pacs_apply",
  "method": "POST",
  "headers": {
    "Content-Type": "text/xml;charset=UTF-8"
  },
  "soupTemplate": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:her=\"http://herenit.com\">\n  <soapenv:Header></soapenv:Header>\n  <soapenv:Body>\n    <her:MessageIn>\n      <her:DataType>XML</her:DataType>\n      <her:pInput><![CDATA[${inputParam}]]></her:pInput>\n    </her:MessageIn>\n  </soapenv:Body>\n</soapenv:Envelope>",
  "dataType": "xml",
  "escape": false,
  "elementName": "request",
  "namespace": "http://herenit.com",
  "namespaceElementName": "MessageIn",
  "namespaceDataElementName": "pInput",
  "isCDATA": true,
  "objectTag": ["PatientInfo", "OrderInfo"]
}
```

---

### Database节点配置

#### 元数据配置（metaInfo）

Database节点的元数据配置为JSON格式，所有字段均为必填：

**必填字段：**
- `driverClassName` (string, 必填): 数据库驱动类名，用于建立数据库连接
  - SQL Server: `com.microsoft.sqlserver.jdbc.SQLServerDriver`
  - MySQL: `com.mysql.cj.jdbc.Driver`
  - OpenGauss: `org.opengauss.Driver`
- `operation` (string, 必填): 数据库操作类型，支持`SELECT`、`INSERT`、`UPDATE`、`DELETE`
- `url` (string, 必填): 数据库连接URL，包含数据库地址、端口、数据库名等连接信息
  - SQL Server: `jdbc:sqlserver://10.20.4.245:1433;DatabaseName=HEMS_V5;encrypt=false;trustServerCertificate=false`
  - MySQL: `jdbc:mysql://localhost:3306/mydb`
  - OpenGauss: `jdbc:opengauss://172.16.16.147:5433/mydb?currentSchema=hems`
- `username` (string, 必填): 数据库用户名，用于连接数据库的认证信息
- `password` (string, 必填): 数据库密码，用于连接数据库的认证信息
- `sqlTemplate` (string, 必填): SQL模板，通过参数占位符`{paramName}`，框架会将节点参数配置转换后的值填充到占位符位置
  - 示例（单个参数）: `SELECT * FROM ReportDetail WHERE patid= {PatientCode}`
  - 示例（多个参数，包含数组）: `SELECT * FROM ReportDetail WHERE patid= {PatientCode} and Applyno IN {ApplyNoList}`

**配置示例：**
```json
{
  "driverClassName": "com.microsoft.sqlserver.jdbc.SQLServerDriver",
  "operation": "SELECT",
  "url": "jdbc:sqlserver://10.20.4.245:1433;DatabaseName=HEMS_V5;encrypt=false;trustServerCertificate=false",
  "username": "sa",
  "password": "Hjk@2022!@",
  "sqlTemplate": "SELECT * FROM ReportDetail WHERE patid= {PatientCode} and Applyno IN {ApplyNoList}"
}
```

---

## 工作流配置

工作流用于将节点关联起来，定义接口对外暴露的路径和相关信息。

### 工作流字段说明

**必填字段：**
- `flowName` (string, 必填): 工作流名称，建议使用业务场景描述
  - 推荐命名示例：新增账单、建档、申请单分发、报告获取、账单状态通知
- `interfaceUri` (string, 必填): 接口URI，定义对外暴露的接口路径
  - **Jarvis**: 直接使用Jarvis入参中的BusinessMethod字段值
    - 建档接口: `CreateArchives` | 账单创建接口: `SyncHisChagSheet`
  - **三方接口**: 使用`/agent-open-api/**`格式，通配符部分可自定
    - 示例: `/agent-open-api/pacs-status` | `/agent-open-api/lis-report`
  - **自定义Controller**: 可通过编写Controller来实现自定义接口路径
  - 注意：接口URI与工作流是一对一的关系，每个工作流对应一个唯一的接口路径
- `contentType` (string, 必填): 参数类型，根据上游入参格式选择`JSON`或`XML`
  - **JSON**: 适用于RESTful协议，框架支持自动解析为key-value格式
  - **XML**: 适用于SOAP协议，框架支持规范的XML格式解析，对于非规范的XML只能通过自定义Controller实现
- `firstFlowNodes` (array, 必填): 首节点ID数组，根据上游需要调用的接口按需配置
  - **单节点**: 适用于单一业务场景，如：建档、创建账单、患者信息查询
  - **多节点**: 适用于复杂业务场景，如：创建申请单、报告获取、账单状态通知
  - **多节点编排逻辑**: 框架会并行处理各个节点，并且会把入参原封不动的分发到各个节点，需要节点根据自己配置的参数过滤表达式来判断是否需要截断节点调用或参数过滤

**可选字段：**
- `contentMetaInfo` (string, 可选): 入参元信息，用于描述接口的元数据信息，可以帮助理解接口的结构和用途
- `status` (integer, 可选): 状态，控制工作流的启用/禁用状态，默认值为1（启用）
  - `1`: 启用（工作流正常可用，上游系统可以调用该接口）
  - `0`: 禁用（工作流暂时不可用，可用于临时关闭接口或维护场景）

### 工作流创建流程

1. 先创建节点（节点创建时`flowId`可先不填）
2. 创建工作流（`firstFlowNodes`可先为空数组）
3. 更新工作流，将节点ID添加到`firstFlowNodes`中

---

## 参数映射配置

参数映射用于配置上下游接口的参数映射关系，实现参数结构的自动转换。

### 参数映射字段说明

**必填字段：**
- `nodeId` (integer, 必填): 关联的节点ID
- `processType` (string, 必填): 处理类型
  - `PRE_PROCESS`: 前置处理（请求参数映射），将上游入参转换为目标接口所需的格式
  - `POST_PROCESS`: 后置处理（响应参数映射），将目标接口的响应转换为上游期望的格式
- `sourceParamKey` (string, 可选): 源参数路径，上游传入的参数路径
  - 使用`.`符号访问嵌套对象的字段，如：`request.patientId`、`patientInfo.patientCode`
  - 对于后置处理，如果`mappingSource`为`RESPONSE`，则从响应中取值
  - 对于后置处理，如果`mappingSource`为`INPUT`，则从原始入参取值
  - 对于固定值映射，`sourceParamKey`可为`null`
- `targetParamKey` (string, 必填): 目标参数名，目标接口需要的参数名
- `sourceParamType` (string, 必填): 源参数类型
  - `STRING`: 字符串类型
  - `INTEGER`: 整数类型
  - `OBJECT`: 对象类型
  - `ARRAY`: 数组类型
  - `NONE`: 无类型（用于固定值或表达式映射）
- `targetParamType` (string, 必填): 目标参数类型，同上
- `mappingType` (string, 必填): 映射类型
  - `NAME`: 值映射，直接取值
  - `CONSTANT`: 固定值，填写固定值
  - `EXPRESSION`: 表达式，使用SpEL表达式转换
- `mappingSource` (string, 可选): 映射来源
  - `null`: 前置处理时使用，从上游入参取值
  - `RESPONSE`: 后置处理时使用，从响应取值
  - `INPUT`: 后置处理时使用，从原始入参取值
- `mappingRule` (string, 可选): 映射规则
  - 当`mappingType`为`CONSTANT`时，填写固定值
  - 当`mappingType`为`EXPRESSION`时，填写SpEL表达式
  - 当`mappingType`为`NAME`时，可为`null`
- `paramDesc` (string, 可选): 参数描述，用于说明该参数的用途
- `sort` (integer, 可选): 排序，用于控制参数映射的执行顺序，默认为0
- `parentId` (integer, 可选): 父参数ID，用于嵌套结构映射
- `keepSingleElementKey` (string, 可选): 保留单元素键，用于数组映射场景

### 映射类型说明

#### 1. 值映射（NAME）

直接取值，将源参数的值直接映射到目标参数。

**前置处理示例：**
```json
{
  "processType": "PRE_PROCESS",
  "sourceParamKey": "patientCode",
  "sourceParamType": "STRING",
  "targetParamKey": "patientCode",
  "targetParamType": "STRING",
  "mappingType": "NAME",
  "mappingSource": null,
  "mappingRule": null
}
```

**后置处理示例（从响应取值）：**
```json
{
  "processType": "POST_PROCESS",
  "sourceParamKey": "data",
  "sourceParamType": "OBJECT",
  "targetParamKey": "rsp",
  "targetParamType": "OBJECT",
  "mappingType": "NAME",
  "mappingSource": "RESPONSE",
  "mappingRule": null
}
```

#### 2. 固定值映射（CONSTANT）

使用固定值，不依赖源参数。

**示例：**
```json
{
  "processType": "POST_PROCESS",
  "sourceParamKey": null,
  "sourceParamType": "NONE",
  "targetParamKey": "SendSystemID",
  "targetParamType": "STRING",
  "mappingType": "CONSTANT",
  "mappingSource": "RESPONSE",
  "mappingRule": "APP0013"
}
```

#### 3. 表达式映射（EXPRESSION）

使用SpEL表达式进行复杂转换。

**示例（响应状态码处理）：**
```json
{
  "processType": "POST_PROCESS",
  "sourceParamKey": null,
  "sourceParamType": "NONE",
  "targetParamKey": "code",
  "targetParamType": "STRING",
  "mappingType": "EXPRESSION",
  "mappingSource": "RESPONSE",
  "mappingRule": "#response['code'] != null ? #response['code'].toString() : '200'"
}
```

**示例（响应消息处理）：**
```json
{
  "processType": "POST_PROCESS",
  "sourceParamKey": null,
  "sourceParamType": "NONE",
  "targetParamKey": "message",
  "targetParamType": "STRING",
  "mappingType": "EXPRESSION",
  "mappingSource": "RESPONSE",
  "mappingRule": "#response['message'] != null ? #response['message'] : '成功'"
}
```

### 数据库查询结果映射

对于Database节点，查询结果默认以`data`字段返回（数组格式）。后置处理时，需要将数据库字段（通常为小写）映射到目标字段（通常为大写）。

**示例：**
```json
{
  "processType": "POST_PROCESS",
  "sourceParamKey": "sendsystemid",
  "sourceParamType": "STRING",
  "targetParamKey": "SendSystemID",
  "targetParamType": "STRING",
  "mappingType": "NAME",
  "mappingSource": "RESPONSE",
  "mappingRule": null
}
```

---

## 配置示例

### 完整配置流程示例：个人建档接口

#### 1. 创建工作流节点

```json
{
  "nodeName": "HBOS个人建档节点",
  "nodeType": "HTTP",
  "metaInfo": "{\"url\":\"http://gapi-c2f.seenew.info/hbos-test/open-api/gw-api/hbos-business-thirdparty-integration/common/rhc/createResidentOrgRegistInfo\",\"method\":\"POST\",\"headers\":{\"App-Key\":\"mvTLtFJGmectBac8unwIf1bF\",\"Content-Type\":\"application/json\"}}",
  "keywordField": "patientCode"
}
```

#### 2. 创建工作流

```json
{
  "flowName": "个人建档接口",
  "interfaceUri": "CreateArchives",
  "contentType": "JSON",
  "contentMetaInfo": "HBOS个人建档接口",
  "firstFlowNodes": [],
  "status": 1
}
```

#### 3. 更新工作流（设置首节点）

```json
{
  "flowId": 1,
  "flowName": "个人建档接口",
  "interfaceUri": "CreateArchives",
  "contentType": "JSON",
  "contentMetaInfo": "HBOS个人建档接口",
  "firstFlowNodes": [1],
  "status": 1
}
```

#### 4. 配置前置处理参数映射

```json
[
  {
    "processType": "PRE_PROCESS",
    "nodeId": 1,
    "sourceParamKey": "patientCode",
    "sourceParamType": "STRING",
    "targetParamKey": "patientCode",
    "targetParamType": "STRING",
    "mappingType": "NAME",
    "mappingSource": null,
    "mappingRule": null,
    "paramDesc": "体检编号"
  },
  {
    "processType": "PRE_PROCESS",
    "nodeId": 1,
    "sourceParamKey": "patientName",
    "sourceParamType": "STRING",
    "targetParamKey": "name",
    "targetParamType": "STRING",
    "mappingType": "NAME",
    "mappingSource": null,
    "mappingRule": null,
    "paramDesc": "患者姓名"
  }
]
```

#### 5. 配置后置处理参数映射

```json
[
  {
    "processType": "POST_PROCESS",
    "nodeId": 1,
    "sourceParamKey": "data",
    "sourceParamType": "OBJECT",
    "targetParamKey": "rsp",
    "targetParamType": "OBJECT",
    "mappingType": "NAME",
    "mappingSource": "RESPONSE",
    "mappingRule": null,
    "paramDesc": "响应数据对象"
  },
  {
    "processType": "POST_PROCESS",
    "nodeId": 1,
    "sourceParamKey": null,
    "sourceParamType": "NONE",
    "targetParamKey": "code",
    "targetParamType": "STRING",
    "mappingType": "EXPRESSION",
    "mappingSource": "RESPONSE",
    "mappingRule": "#response['code'] != null ? #response['code'].toString() : '200'",
    "paramDesc": "响应状态码"
  }
]
```

---

## 注意事项

1. **节点创建顺序**: 先创建节点，再创建工作流，最后更新工作流设置首节点
2. **参数映射顺序**: 前置处理用于请求参数转换，后置处理用于响应参数转换
3. **字段命名**: 注意源字段和目标字段的大小写，特别是数据库查询结果通常为小写
4. **表达式语法**: 使用SpEL表达式时，注意语法正确性，可通过Mock测试验证
5. **多节点编排**: 多节点时，框架会并行处理，每个节点需要配置参数过滤表达式来决定是否调用
6. **数组拆分**: 当三方接口不支持批量接收时，使用`splitField`字段进行数组拆分
7. **参数过滤**: 使用`paramFilterExpr`可以过滤不需要调用的数据，减少无效调用

---

## MCP接口说明

AI可以通过以下MCP接口自动化创建配置：

1. **工作流管理**: `/v1/ai/mcp/workflow`
   - `POST /create`: 创建工作流
   - `POST /update`: 更新工作流
   - `POST /delete`: 删除工作流

2. **工作流节点管理**: `/v1/ai/mcp/workflow-node`
   - `POST /create`: 创建节点
   - `POST /update`: 更新节点
   - `POST /delete`: 删除节点

3. **节点参数配置管理**: `/v1/ai/mcp/node-param-config`
   - `POST /create`: 创建参数配置
   - `POST /update`: 更新参数配置
   - `POST /delete`: 删除参数配置

所有接口返回格式：
```json
{
  "code": "200",
  "message": "success",
  "rsp": <返回数据>
}
```

