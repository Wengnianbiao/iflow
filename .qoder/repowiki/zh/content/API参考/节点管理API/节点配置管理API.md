# 节点配置管理API

<cite>
**本文档引用文件**   
- [McpWorkflowNodeController.java](file://agent-ai/src/main/java/com/helianhealth/agent/ai/controller/McpWorkflowNodeController.java)
- [InterfaceWorkflowNodeServiceImpl.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/service/impl/InterfaceWorkflowNodeServiceImpl.java)
- [NodeParamConfigExportRsp.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/reponse/NodeParamConfigExportRsp.java)
- [NodeConfigExportRsp.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/reponse/NodeConfigExportRsp.java)
- [NodeConfigImportDTO.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/model/dto/NodeConfigImportDTO.java)
- [application.yml](file://agent-web/src/main/resources/application.yml)
- [batch_import_example.json](file://agent-ai/batch_import_example.json)
</cite>

## 目录
1. [批量操作接口](#批量操作接口)
2. [配置导入导出功能](#配置导入导出功能)
3. [响应结构说明](#响应结构说明)
4. [MultipartFile处理最佳实践](#multipartfile处理最佳实践)
5. [JSON配置文件示例](#json配置文件示例)
6. [导入导出操作流程](#导入导出操作流程)

## 批量操作接口

### 批量删除接口 (/batch-delete)

批量删除接口 `/v1/ai/mcp/workflow-node/batch-delete` 提供了同时删除多个工作流节点的功能。该接口采用DELETE方法，接收一个包含节点ID的JSON数组作为请求体。

**事务处理机制**：  
该接口在服务层实现了事务管理，通过`@Transactional("workflowAgentTransactionManager")`注解确保操作的原子性。当执行批量删除时，系统会为每个节点执行完整的删除流程，包括删除节点本身及其关联的所有参数配置。

**部分成功场景处理策略**：  
接口采用"尽力而为"的处理策略。即使某些节点删除失败，系统也会继续处理剩余节点。具体实现逻辑如下：
- 遍历传入的节点ID列表
- 对每个节点尝试执行删除操作
- 如果删除失败，记录错误日志但不中断整体流程
- 返回实际成功删除的节点数量

这种设计确保了在大规模操作中，即使部分数据存在问题，也不会影响其他有效数据的处理。

**Section sources**
- [McpWorkflowNodeController.java](file://agent-ai/src/main/java/com/helianhealth/agent/ai/controller/McpWorkflowNodeController.java#L84-L94)
- [InterfaceWorkflowNodeServiceImpl.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/service/impl/InterfaceWorkflowNodeServiceImpl.java#L162-L180)

### 批量导入接口 (/batch-import)

批量导入接口 `/v1/ai/mcp/workflow-node/batch-import` 支持通过上传JSON文件来批量创建工作流节点。该接口采用POST方法，使用`multipart/form-data`内容类型。

**文件格式要求**：
- 文件必须为JSON格式
- 文件扩展名应为`.json`
- 支持两种数据结构：单个节点配置对象或节点配置对象数组

**数据校验规则**：
1. 文件不能为空
2. 文件必须是有效的JSON格式
3. 节点配置必须包含必要的字段（如节点名称、节点类型等）
4. 参数映射规则必须符合系统定义的规范

**异常处理流程**：
- 文件为空：抛出"文件不能为空"异常
- 文件格式错误：抛出"文件格式不正确，仅支持JSON格式文件"异常
- JSON解析失败：抛出"JSON文件格式不正确"异常
- 数据库操作失败：记录错误日志并向上抛出运行时异常

**Section sources**
- [McpWorkflowNodeController.java](file://agent-ai/src/main/java/com/helianhealth/agent/ai/controller/McpWorkflowNodeController.java#L99-L110)
- [InterfaceWorkflowNodeServiceImpl.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/service/impl/InterfaceWorkflowNodeServiceImpl.java#L299-L346)

## 配置导入导出功能

### 配置导出接口 (/export, /export-node)

系统提供了两个配置导出接口：
- `/v1/console/node/export/{nodeId}`：导出节点参数配置
- `/v1/console/node/export-node/{nodeId}`：导出完整节点配置

**设计原理**：
导出功能的核心是将数据库中的配置数据转换为结构化的JSON响应。系统通过递归算法构建参数的树形结构，确保父子参数关系的完整保留。

**数据处理流程**：
1. 根据节点ID查询所有相关参数配置
2. 构建节点基本信息
3. 使用递归算法构建参数树形结构
4. 返回包含完整配置的响应对象

### 配置导入接口 (/import, /import-node)

系统提供了两个配置导入接口：
- `/v1/console/node/import`：导入节点参数配置
- `/v1/console/node/import-node`：导入完整节点配置

**设计原理**：
导入功能采用分步处理策略，先创建节点基本信息，再逐级创建参数配置。这种设计确保了数据完整性和引用关系的正确建立。

**数据处理流程**：
1. 验证上传文件的格式和内容
2. 解析JSON内容为DTO对象
3. 创建节点基本信息
4. 递归创建参数配置树

**Section sources**
- [WorkflowNodeController.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/WorkflowNodeController.java#L138-L207)
- [InterfaceWorkflowNodeServiceImpl.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/service/impl/InterfaceWorkflowNodeServiceImpl.java#L266-L296)

## 响应结构说明

### NodeParamConfigExportRsp 响应结构

`NodeParamConfigExportRsp` 是节点参数导出的响应结构，包含以下字段：

**NodeInfo（节点信息）**：
- `nodeName`：节点名称
- `nodeType`：节点类型

**NodeParamConfigExportDTO（参数配置）**：
- `processType`：处理类型
- `sourceParamKey`：源参数键
- `sourceParamType`：源参数类型
- `paramDesc`：参数描述
- `targetParamKey`：目标参数键
- `targetParamType`：目标参数类型
- `keepSingleElementKey`：是否保留单元素键
- `sort`：排序序号
- `mappingType`：映射类型
- `mappingSource`：映射源
- `mappingRule`：映射规则
- `childParam`：子参数列表（支持嵌套）

### NodeConfigExportRsp 响应结构

`NodeConfigExportRsp` 是完整节点导出的响应结构，在`NodeParamConfigExportRsp`的基础上增加了更多节点元信息：

**NodeInfo（节点信息）**：
- `nodeName`：节点名称
- `nodeType`：节点类型
- `metaInfo`：元信息
- `paramFilterExpr`：参数过滤表达式
- `keywordField`：关键字字段

**NodeParamConfigDTO（参数配置）**：  
字段与`NodeParamConfigExportDTO`相同，支持完整的参数映射配置。

**Section sources**
- [NodeParamConfigExportRsp.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/reponse/NodeParamConfigExportRsp.java#L1-L47)
- [NodeConfigExportRsp.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/controller/reponse/NodeConfigExportRsp.java#L1-L52)

## MultipartFile处理最佳实践

### 文件大小限制

系统通过Spring配置文件`application.yml`中的`multipart`配置项来管理文件上传限制：

```yaml
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: -1
      max-request-size: -1
```

当前配置为`-1`，表示不限制文件大小。在生产环境中，建议设置合理的限制值，如`10MB`，以防止恶意大文件上传。

### 格式验证

系统实施了严格的文件格式验证：
- 检查文件是否为空
- 验证文件扩展名是否为`.json`
- 确保文件名存在且非空
- 验证JSON内容的语法正确性

### 安全防护措施

系统采用了多层安全防护：
1. **文件类型验证**：只允许JSON文件上传
2. **内容解析防护**：使用try-catch块捕获JSON解析异常
3. **事务管理**：数据库操作在事务中执行，确保数据一致性
4. **日志记录**：详细记录导入导出操作的日志，便于审计和故障排查
5. **字符编码**：统一使用UTF-8编码处理文件内容

**Section sources**
- [application.yml](file://agent-web/src/main/resources/application.yml#L10-L13)
- [InterfaceWorkflowNodeServiceImpl.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/service/impl/InterfaceWorkflowNodeServiceImpl.java#L269-L276)

## JSON配置文件示例

以下是一个完整的节点配置JSON文件示例：

```json
{
  "nodeInfo": {
    "nodeName": "数据同步节点",
    "nodeType": "HTTP",
    "metaInfo": "{\"timeout\":30000}",
    "paramFilterExpr": "status==1",
    "keywordField": "patientId"
  },
  "paramsConfig": [
    {
      "processType": "REQUEST",
      "sourceParamKey": "patient.name",
      "sourceParamType": "STRING",
      "paramDesc": "患者姓名",
      "targetParamKey": "name",
      "targetParamType": "STRING",
      "keepSingleElementKey": true,
      "sort": 1,
      "mappingType": "DIRECT",
      "mappingSource": "SOURCE",
      "mappingRule": "",
      "childParam": [
        {
          "processType": "REQUEST",
          "sourceParamKey": "first",
          "sourceParamType": "STRING",
          "paramDesc": "名",
          "targetParamKey": "firstName",
          "targetParamType": "STRING",
          "sort": 1,
          "mappingType": "DIRECT",
          "mappingSource": "SOURCE",
          "mappingRule": ""
        }
      ]
    }
  ]
}
```

此示例展示了如何配置一个包含嵌套参数的HTTP节点，包括节点基本信息和参数映射规则。

**Section sources**
- [batch_import_example.json](file://agent-ai/batch_import_example.json)

## 导入导出操作流程

### 配置导出完整流程

1. **发起导出请求**：客户端向`/export-node/{nodeId}`发送GET请求
2. **查询数据**：服务端根据节点ID查询节点基本信息和所有参数配置
3. **构建响应**：将查询结果转换为`NodeConfigExportRsp`对象
4. **构建树形结构**：使用递归算法组织参数的父子关系
5. **返回结果**：将结构化数据作为JSON响应返回

### 配置导入完整流程

1. **准备JSON文件**：按照规范格式准备配置文件
2. **发起导入请求**：通过POST请求上传文件到`/import-node`
3. **文件验证**：系统验证文件格式和内容有效性
4. **解析JSON**：将文件内容解析为`NodeConfigImportDTO`对象
5. **创建节点**：先创建节点基本信息
6. **导入参数**：递归创建参数配置树
7. **返回结果**：成功则返回成功响应，失败则返回错误信息

### 批量操作流程

**批量删除流程**：
1. 准备节点ID数组
2. 发送DELETE请求到`/batch-delete`
3. 系统逐个处理每个节点的删除
4. 返回成功删除的数量统计

**批量导入流程**：
1. 准备包含多个节点配置的JSON文件
2. 发送POST请求到`/batch-import`
3. 系统解析文件内容
4. 对每个节点配置执行导入操作
5. 返回整体操作结果

**Section sources**
- [InterfaceWorkflowNodeServiceImpl.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/service/impl/InterfaceWorkflowNodeServiceImpl.java#L213-L265)
- [InterfaceWorkflowNodeServiceImpl.java](file://agent-workflow/src/main/java/com/helianhealth/agent/workflow/service/impl/InterfaceWorkflowNodeServiceImpl.java#L299-L346)