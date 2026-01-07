# API参考

<cite>
**本文档中引用的文件**  
- [WorkflowController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowController.java)
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java)
- [NodeParamConfigController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeParamConfigController.java)
- [ApiLogController.java](file://agent-log/src/main/java/com/helianhealth/agent/log/controller/ApiLogController.java)
- [ApiController.java](file://agent-api/src/main/java/com/helianhealth/agent/api/controller/ApiController.java)
- [MockInvokeController.java](file://agent-api/src/main/java/com/helianhealth/agent/api/controller/MockInvokeController.java)
- [InterfaceWorkflowDO.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/model/domain/InterfaceWorkflowDO.java)
- [WorkflowNodeCreateReq.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/request/WorkflowNodeCreateReq.java)
- [WorkflowNodeUpdateReq.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/request/WorkflowNodeUpdateReq.java)
- [NodeParamConfigDO.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/model/domain/NodeParamConfigDO.java)
- [InterfaceInvokeLogDO.java](file://agent-log/src/main/java/com/helianhealth/agent/log/model/InterfaceInvokeLogDO.java)
- [ResultData.java](file://agent-common/src/main/java/com/helianhealth/agent/common/base/ResultData.java)
- [NodeExtensionController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeExtensionController.java)
</cite>

## 目录
1. [工作流管理API](#工作流管理api)
2. [节点管理API](#节点管理api)
3. [节点参数配置API](#节点参数配置api)
4. [调用日志查询API](#调用日志查询api)
5. [核心执行端点](#核心执行端点)
6. [节点扩展管理API](#节点扩展管理api)
7. [通用响应结构](#通用响应结构)
8. [错误码说明](#错误码说明)
9. [使用示例](#使用示例)

## 工作流管理API

提供对工作流的增删改查操作，所有端点位于 `/v1/console/workflow` 路径下。

### 获取工作流列表（分页）

**HTTP方法**: GET  
**URL模式**: `/v1/console/workflow/all`  
**认证方式**: 无（假设系统已配置全局认证）  
**参数说明**:
- `pageNum` (可选): 页码，默认为1
- `pageSize` (可选): 每页数量，默认为10

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": {
    "total": 10,
    "list": [
      {
        "flowId": 1,
        "flowName": "工作流名称",
        "interfaceUri": "/api/uri",
        "contentType": "JSON",
        "contentMetaInfo": "{}",
        "firstFlowNodes": [1, 2],
        "status": 1
      }
    ],
    "pageNum": 1,
    "pageSize": 10
  }
}
```

**Section sources**
- [WorkflowController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowController.java#L35-L46)

### 获取工作流列表（不分页）

**HTTP方法**: GET  
**URL模式**: `/v1/console/workflow/all-unpaged`  
**认证方式**: 无  
**参数说明**: 无

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": [
    {
      "flowId": 1,
      "flowName": "工作流名称",
      "interfaceUri": "/api/uri",
      "contentType": "JSON",
      "contentMetaInfo": "{}",
      "firstFlowNodes": [1, 2],
      "status": 1
    }
  ]
}
```

**Section sources**
- [WorkflowController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowController.java#L51-L60)

### 创建工作流

**HTTP方法**: POST  
**URL模式**: `/v1/console/workflow/create`  
**认证方式**: 无  
**请求体结构**:
```json
{
  "flowName": "新工作流",
  "interfaceUri": "/api/new-workflow",
  "contentType": "JSON",
  "contentMetaInfo": "{}",
  "firstFlowNodes": [1],
  "status": 1
}
```

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": 1
}
```
`rsp` 字段为新创建工作流的ID。

**Section sources**
- [WorkflowController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowController.java#L65-L74)
- [InterfaceWorkflowDO.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/model/domain/InterfaceWorkflowDO.java)

### 更新工作流

**HTTP方法**: POST  
**URL模式**: `/v1/console/workflow/update`  
**认证方式**: 无  
**请求体结构**:
```json
{
  "flowId": 1,
  "flowName": "更新后的工作流",
  "interfaceUri": "/api/updated-workflow",
  "contentType": "JSON",
  "contentMetaInfo": "{}",
  "firstFlowNodes": [1, 2],
  "status": 1
}
```

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": 1
}
```
`rsp` 字段为受影响的记录数。

**Section sources**
- [WorkflowController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowController.java#L79-L88)
- [InterfaceWorkflowDO.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/model/domain/InterfaceWorkflowDO.java)

### 删除工作流

**HTTP方法**: DELETE  
**URL模式**: `/v1/console/workflow/delete/{flowId}`  
**认证方式**: 无  
**路径参数**:
- `flowId`: 要删除的工作流ID

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": "工作流删除成功"
}
```

**Section sources**
- [WorkflowController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowController.java#L93-L103)

## 节点管理API

提供对工作流节点的增删改查操作，所有端点位于 `/v1/console/node` 路径下。

### 获取节点列表（分页）

**HTTP方法**: GET  
**URL模式**: `/v1/console/node/all`  
**认证方式**: 无  
**参数说明**:
- `flowId` (可选): 工作流ID，用于过滤特定工作流的节点
- `pageNum` (可选): 页码，默认为1
- `pageSize` (可选): 每页数量，默认为10

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": {
    "total": 5,
    "list": [
      {
        "nodeId": 1,
        "nodeName": "节点名称",
        "flowId": 1,
        "nodeType": "HTTP",
        "paramFilterExpr": "",
        "metaInfo": "{}",
        "scheduleExpr": "",
        "scheduleParamSourceType": "",
        "keywordField": "",
        "splitField": "",
        "remoteExtension": "",
        "createTime": "2023-01-01T00:00:00"
      }
    ],
    "pageNum": 1,
    "pageSize": 10
  }
}
```

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L39-L52)

### 获取节点列表（不分页）

**HTTP方法**: GET  
**URL模式**: `/v1/console/node/all-unpaged`  
**认证方式**: 无  
**参数说明**: 无

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": [
    {
      "nodeId": 1,
      "nodeName": "节点名称",
      "flowId": 1,
      "nodeType": "HTTP",
      "paramFilterExpr": "",
      "metaInfo": "{}",
      "scheduleExpr": "",
      "scheduleParamSourceType": "",
      "keywordField": "",
      "splitField": "",
      "remoteExtension": "",
      "createTime": "2023-01-01T00:00:00"
    }
  ]
}
```

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L54-L64)

### 创建工作流节点

**HTTP方法**: POST  
**URL模式**: `/v1/console/node/create`  
**认证方式**: 无  
**请求体结构**:
```json
{
  "nodeName": "新节点",
  "flowId": 1,
  "nodeType": "HTTP",
  "paramFilterExpr": "",
  "metaInfo": "{}",
  "scheduleExpr": "",
  "scheduleParamSourceType": "",
  "keywordField": "",
  "splitField": "",
  "remoteExtension": ""
}
```

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": 1
}
```
`rsp` 字段为新创建节点的ID。

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L71-L80)
- [WorkflowNodeCreateReq.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/request/WorkflowNodeCreateReq.java)

### 根据ID获取工作流节点

**HTTP方法**: GET  
**URL模式**: `/v1/console/node/{nodeId}`  
**认证方式**: 无  
**路径参数**:
- `nodeId`: 节点ID

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": {
    "nodeId": 1,
    "nodeName": "节点名称",
    "flowId": 1,
    "nodeType": "HTTP",
    "paramFilterExpr": "",
    "metaInfo": "{}",
    "scheduleExpr": "",
    "scheduleParamSourceType": "",
    "keywordField": "",
    "splitField": "",
    "remoteExtension": "",
    "createTime": "2023-01-01T00:00:00"
  }
}
```

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L85-L97)

### 更新工作流节点

**HTTP方法**: POST  
**URL模式**: `/v1/console/node/update`  
**认证方式**: 无  
**请求体结构**:
```json
{
  "nodeId": 1,
  "nodeName": "更新后的节点",
  "flowId": 1,
  "nodeType": "HTTP",
  "paramFilterExpr": "",
  "metaInfo": "{}",
  "scheduleExpr": "",
  "scheduleParamSourceType": "",
  "keywordField": "",
  "splitField": "",
  "remoteExtension": ""
}
```

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": 1
}
```
`rsp` 字段为受影响的记录数。

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L102-L111)
- [WorkflowNodeUpdateReq.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/request/WorkflowNodeUpdateReq.java)

### 删除工作流节点

**HTTP方法**: DELETE  
**URL模式**: `/v1/console/node/{nodeId}`  
**认证方式**: 无  
**路径参数**:
- `nodeId`: 要删除的节点ID

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": "工作流节点删除成功"
}
```

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L116-L125)

### 删除节点所有配置

**HTTP方法**: DELETE  
**URL模式**: `/v1/console/node/{nodeId}/delete-all-config`  
**认证方式**: 无  
**路径参数**:
- `nodeId`: 节点ID

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": "工作流节点配置删除成功"
}
```

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L127-L136)

### 导出节点参数配置

**HTTP方法**: GET  
**URL模式**: `/v1/console/node/export/{nodeId}`  
**认证方式**: 无  
**路径参数**:
- `nodeId`: 节点ID

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": {
    "nodeId": 1,
    "nodeParamConfigs": [
      {
        "configId": 1,
        "processType": "PRE_PROCESS",
        "nodeId": 1,
        "parentId": null,
        "sourceParamKey": "sourceKey",
        "sourceParamType": "STRING",
        "paramDesc": "参数描述",
        "targetParamKey": "targetKey",
        "targetParamType": "STRING",
        "keepSingleElementKey": true,
        "sort": 1,
        "mappingType": "NAME_MAPPING",
        "mappingSource": "BUSINESS_DATA",
        "mappingRule": "rule"
      }
    ]
  }
}
```

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L138-L148)

### 导入节点参数配置

**HTTP方法**: POST  
**URL模式**: `/v1/console/node/import`  
**认证方式**: 无  
**表单参数**:
- `nodeId`: 节点ID
- `file`: 包含配置的JSON文件

**响应格式**:
```json
{
  "code": "0",
  "message": "成功"
}
```

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L153-L172)

### 导入节点配置

**HTTP方法**: POST  
**URL模式**: `/v1/console/node/import-node`  
**认证方式**: 无  
**表单参数**:
- `file`: 包含节点配置的JSON文件

**响应格式**:
```json
{
  "code": "0",
  "message": "成功"
}
```

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L177-L191)

### 导出节点配置

**HTTP方法**: GET  
**URL模式**: `/v1/console/node/export-node/{nodeId}`  
**认证方式**: 无  
**路径参数**:
- `nodeId`: 节点ID

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": {
    "nodeId": 1,
    "nodeName": "节点名称",
    "flowId": 1,
    "nodeType": "HTTP",
    "paramFilterExpr": "",
    "metaInfo": "{}",
    "scheduleExpr": "",
    "scheduleParamSourceType": "",
    "keywordField": "",
    "splitField": "",
    "remoteExtension": "",
    "nodeParamConfigs": [
      {
        "configId": 1,
        "processType": "PRE_PROCESS",
        "nodeId": 1,
        "parentId": null,
        "sourceParamKey": "sourceKey",
        "sourceParamType": "STRING",
        "paramDesc": "参数描述",
        "targetParamKey": "targetKey",
        "targetParamType": "STRING",
        "keepSingleElementKey": true,
        "sort": 1,
        "mappingType": "NAME_MAPPING",
        "mappingSource": "BUSINESS_DATA",
        "mappingRule": "rule"
      }
    ]
  }
}
```

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L196-L207)

## 节点参数配置API

提供对节点参数配置的增删改查操作，所有端点位于 `/v1/console/node-param` 路径下。

### 获取节点参数配置列表（分页）

**HTTP方法**: GET  
**URL模式**: `/v1/console/node-param/all`  
**认证方式**: 无  
**参数说明**:
- `nodeId` (可选): 节点ID
- `processType` (可选): 处理类型（PRE_PROCESS/POST_PROCESS）
- `targetParam` (可选): 目标参数键名
- `pageNum` (可选): 页码，默认为1
- `pageSize` (可选): 每页数量，默认为10

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": {
    "total": 3,
    "list": [
      {
        "configId": 1,
        "processType": "PRE_PROCESS",
        "nodeId": 1,
        "parentId": null,
        "sourceParamKey": "sourceKey",
        "sourceParamType": "STRING",
        "paramDesc": "参数描述",
        "targetParamKey": "targetKey",
        "targetParamType": "STRING",
        "keepSingleElementKey": true,
        "sort": 1,
        "mappingType": "NAME_MAPPING",
        "mappingSource": "BUSINESS_DATA",
        "mappingRule": "rule"
      }
    ],
    "pageNum": 1,
    "pageSize": 10
  }
}
```

**Section sources**
- [NodeParamConfigController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeParamConfigController.java#L37-L47)
- [NodeParamConfigDO.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/model/domain/NodeParamConfigDO.java)

### 获取节点参数配置列表（不分页）

**HTTP方法**: GET  
**URL模式**: `/v1/console/node-param/all-unpaged`  
**认证方式**: 无  
**参数说明**:
- `nodeId` (可选): 节点ID
- `processType` (可选): 处理类型（PRE_PROCESS/POST_PROCESS）

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": [
    {
      "configId": 1,
      "processType": "PRE_PROCESS",
      "nodeId": 1,
      "parentId": null,
      "sourceParamKey": "sourceKey",
      "sourceParamType": "STRING",
      "paramDesc": "参数描述",
      "targetParamKey": "targetKey",
      "targetParamType": "STRING",
      "keepSingleElementKey": true,
      "sort": 1,
      "mappingType": "NAME_MAPPING",
      "mappingSource": "BUSINESS_DATA",
      "mappingRule": "rule"
    }
  ]
}
```

**Section sources**
- [NodeParamConfigController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeParamConfigController.java#L56-L67)
- [NodeParamConfigDO.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/model/domain/NodeParamConfigDO.java)

### 创建节点参数配置

**HTTP方法**: POST  
**URL模式**: `/v1/console/node-param/create`  
**认证方式**: 无  
**请求体结构**:
```json
{
  "processType": "PRE_PROCESS",
  "nodeId": 1,
  "parentId": null,
  "sourceParamKey": "sourceKey",
  "sourceParamType": "STRING",
  "paramDesc": "参数描述",
  "targetParamKey": "targetKey",
  "targetParamType": "STRING",
  "keepSingleElementKey": true,
  "sort": 1,
  "mappingType": "NAME_MAPPING",
  "mappingSource": "BUSINESS_DATA",
  "mappingRule": "rule"
}
```

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": {
    "configId": 1,
    "processType": "PRE_PROCESS",
    "nodeId": 1,
    "parentId": null,
    "sourceParamKey": "sourceKey",
    "sourceParamType": "STRING",
    "paramDesc": "参数描述",
    "targetParamKey": "targetKey",
    "targetParamType": "STRING",
    "keepSingleElementKey": true,
    "sort": 1,
    "mappingType": "NAME_MAPPING",
    "mappingSource": "BUSINESS_DATA",
    "mappingRule": "rule"
  }
}
```

**Section sources**
- [NodeParamConfigController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeParamConfigController.java#L72-L98)
- [NodeParamConfigDO.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/model/domain/NodeParamConfigDO.java)

### 更新节点参数配置

**HTTP方法**: POST  
**URL模式**: `/v1/console/node-param/update`  
**认证方式**: 无  
**请求体结构**:
```json
{
  "configId": 1,
  "processType": "PRE_PROCESS",
  "nodeId": 1,
  "parentId": null,
  "sourceParamKey": "updatedSourceKey",
  "sourceParamType": "STRING",
  "paramDesc": "更新后的参数描述",
  "targetParamKey": "updatedTargetKey",
  "targetParamType": "STRING",
  "keepSingleElementKey": false,
  "sort": 2,
  "mappingType": "FIXED_VALUE",
  "mappingSource": "CONSTANT",
  "mappingRule": "fixedValue"
}
```

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": {
    "configId": 1,
    "processType": "PRE_PROCESS",
    "nodeId": 1,
    "parentId": null,
    "sourceParamKey": "updatedSourceKey",
    "sourceParamType": "STRING",
    "paramDesc": "更新后的参数描述",
    "targetParamKey": "updatedTargetKey",
    "targetParamType": "STRING",
    "keepSingleElementKey": false,
    "sort": 2,
    "mappingType": "FIXED_VALUE",
    "mappingSource": "CONSTANT",
    "mappingRule": "fixedValue"
  }
}
```

**Section sources**
- [NodeParamConfigController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeParamConfigController.java#L103-L123)
- [NodeParamConfigDO.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/model/domain/NodeParamConfigDO.java)

### 删除节点参数配置

**HTTP方法**: DELETE  
**URL模式**: `/v1/console/node-param/{configId}`  
**认证方式**: 无  
**路径参数**:
- `configId`: 配置ID

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": "节点参数配置删除成功"
}
```

**Section sources**
- [NodeParamConfigController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeParamConfigController.java#L128-L137)

## 调用日志查询API

提供对接口调用日志的查询功能，端点位于 `/v1/console/api-log` 路径下。

### 查询调用日志

**HTTP方法**: GET  
**URL模式**: `/v1/console/api-log/all`  
**认证方式**: 无  
**参数说明**:
- `nodeName` (可选): 节点名称
- `keyword` (可选): 关键字
- `pageNum` (可选): 页码，默认为1
- `pageSize` (可选): 每页数量，默认为10

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": {
    "total": 100,
    "list": [
      {
        "logId": 1,
        "nodeName": "HTTP节点",
        "keyword": "patient123",
        "businessData": "{\"patientCode\":\"patient123\"}",
        "paramBeforeInvoke": "{\"patientId\":\"patient123\"}",
        "remoteInvokeResponse": "{\"status\":\"success\"}",
        "paramAfterInvoke": "{\"result\":\"success\"}",
        "invokeTime": "150ms",
        "createTime": "2023-01-01T10:00:00"
      }
    ],
    "pageNum": 1,
    "pageSize": 10
  }
}
```

**Section sources**
- [ApiLogController.java](file://agent-log/src/main/java/com/helianhealth/agent/log/controller/ApiLogController.java#L26-L39)
- [InterfaceInvokeLogDO.java](file://agent-log/src/main/java/com/helianhealth/agent/log/model/InterfaceInvokeLogDO.java)

## 核心执行端点

系统的核心执行端点，用于触发工作流执行。

### 执行工作流

**HTTP方法**: POST  
**URL模式**: `/HLOpenApi/Hjk`  
**认证方式**: 无  
**请求体结构**:
```json
{
  "interfaceUri": "/api/workflow/execute",
  "businessData": {
    "patientCode": "P001",
    "visitId": "V001"
  }
}
```

**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": {
    "result": "success",
    "data": {}
  }
}
```

**Section sources**
- [ApiController.java](file://agent-api/src/main/java/com/helianhealth/agent/api/controller/ApiController.java#L32-L35)

### 健康检查

**HTTP方法**: GET  
**URL模式**: `/HLOpenApi/Hjk`  
**认证方式**: 无  
**参数说明**: 无

**响应格式**:
```json
{
  "code": "0",
  "message": "成功"
}
```

**Section sources**
- [ApiController.java](file://agent-api/src/main/java/com/helianhealth/agent/api/controller/ApiController.java#L27-L30)

### 开放API调用

**HTTP方法**: POST  
**URL模式**: `/agent-open-api/**`  
**认证方式**: 无  
**请求头**:
- Content-Type: application/json, application/xml, text/xml, text/plain
- Accept: application/json, application/xml

**请求体**: 根据具体接口而定

**响应格式**: 根据具体接口而定

**Section sources**
- [ApiController.java](file://agent-api/src/main/java/com/helianhealth/agent/api/controller/ApiController.java#L37-L48)

## 节点扩展管理API

提供对节点扩展的管理功能，端点位于 `/api/node-extension` 路径下。

### 刷新节点扩展

**HTTP方法**: POST  
**URL模式**: `/api/node-extension/refresh/{codeId}`  
**认证方式**: 无  
**路径参数**:
- `codeId`: 扩展代码ID

**响应格式**: 无内容（HTTP 200）

**Section sources**
- [NodeExtensionController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeExtensionController.java#L40-L43)

### 注册节点扩展

**HTTP方法**: POST  
**URL模式**: `/api/node-extension/register`  
**认证方式**: 无  
**请求体结构**:
```json
{
  "codeId": 1,
  "codeName": "扩展名称",
  "codeContent": "扩展代码内容",
  "status": 1
}
```

**响应格式**: 无内容（HTTP 200）

**Section sources**
- [NodeExtensionController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeExtensionController.java#L48-L56)

### 更新节点扩展

**HTTP方法**: POST  
**URL模式**: `/api/node-extension/update`  
**认证方式**: 无  
**请求体结构**:
```json
{
  "codeId": 1,
  "codeName": "更新后的扩展名称",
  "codeContent": "更新后的扩展代码内容",
  "status": 1
}
```

**响应格式**: 无内容（HTTP 200）

**Section sources**
- [NodeExtensionController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeExtensionController.java#L61-L64)

### 禁用节点扩展

**HTTP方法**: POST  
**URL模式**: `/api/node-extension/disable/{codeId}`  
**认证方式**: 无  
**路径参数**:
- `codeId`: 扩展代码ID

**响应格式**: 无内容（HTTP 200）

**Section sources**
- [NodeExtensionController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeExtensionController.java#L69-L72)

### 查看已加载的扩展

**HTTP方法**: GET  
**URL模式**: `/api/node-extension/loaded`  
**认证方式**: 无  
**响应格式**:
```json
{
  "extension1": "class com.example.Extension1",
  "extension2": "class com.example.Extension2"
}
```

**Section sources**
- [NodeExtensionController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeExtensionController.java#L77-L80)

### 查询启用的扩展配置

**HTTP方法**: GET  
**URL模式**: `/api/node-extension/enabled-configs`  
**认证方式**: 无  
**响应格式**:
```json
{
  "code": "0",
  "message": "成功",
  "rsp": [
    {
      "codeId": 1,
      "codeName": "扩展名称",
      "codeContent": "扩展代码内容",
      "status": 1
    }
  ]
}
```

**Section sources**
- [NodeExtensionController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/NodeExtensionController.java#L85-L88)

## 通用响应结构

所有API响应均遵循统一的响应结构，由 `ResultData<T>` 类定义。

```json
{
  "code": "0",
  "message": "成功",
  "rsp": {},
  "alertMsg": "",
  "reqParam": "",
  "resParam": "",
  "errorType": 0
}
```

**字段说明**:
- `code`: 响应码，"0"表示成功，其他为错误码
- `message`: 响应消息
- `rsp`: 响应数据，具体结构根据接口而定
- `alertMsg`: 警告消息
- `reqParam`: 请求参数（用于错误追踪）
- `resParam`: 响应参数（用于错误追踪）
- `errorType`: 错误类型

**Section sources**
- [ResultData.java](file://agent-common/src/main/java/com/helianhealth/agent/common/base/ResultData.java)

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| -1 | 失败 |
| 400 | 请求参数错误 |
| 404 | 资源未找到 |
| 500 | 服务器内部错误 |

**Section sources**
- [ResultData.java](file://agent-common/src/main/java/com/helianhealth/agent/common/base/ResultData.java)
- [ResultStatusCode.java](file://agent-common/src/main/java/com/helianhealth/agent/common/base/ResultStatusCode.java)

## 使用示例

### 创建工作流

```bash
curl -X POST http://localhost:8080/v1/console/workflow/create \
  -H "Content-Type: application/json" \
  -d '{
    "flowName": "患者档案创建工作流",
    "interfaceUri": "/api/create-patient-record",
    "contentType": "JSON",
    "contentMetaInfo": "{}",
    "firstFlowNodes": [1],
    "status": 1
  }'
```

### 查询工作流列表

```bash
curl -X GET "http://localhost:8080/v1/console/workflow/all?pageNum=1&pageSize=10"
```

### 创建工作流节点

```bash
curl -X POST http://localhost:8080/v1/console/node/create \
  -H "Content-Type: application/json" \
  -d '{
    "nodeName": "PACS报告查询",
    "flowId": 1,
    "nodeType": "HTTP",
    "paramFilterExpr": "",
    "metaInfo": "{\"url\":\"http://pacs.example.com/api/report\",\"method\":\"GET\",\"headers\":{}}",
    "scheduleExpr": "",
    "scheduleParamSourceType": "",
    "keywordField": "patientCode",
    "splitField": "",
    "remoteExtension": ""
  }'
```

### 执行工作流

```bash
curl -X POST http://localhost:8080/HLOpenApi/Hjk \
  -H "Content-Type: application/json" \
  -d '{
    "interfaceUri": "/api/create-patient-record",
    "businessData": {
      "patientCode": "P001",
      "name": "张三"
    }
  }'
```

### 查询调用日志

```bash
curl -X GET "http://localhost:8080/v1/console/api-log/all?nodeName=PACS报告查询&keyword=P001&pageNum=1&pageSize=10"
```

**Section sources**
- [WorkflowController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowController.java)
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java)
- [ApiController.java](file://agent-api/src/main/java/com/helianhealth/agent/api/controller/ApiController.java)
- [ApiLogController.java](file://agent-log/src/main/java/com/helianhealth/agent/log/controller/ApiLogController.java)