---
trigger: always_on
---
# AI决策指南

> **本文档说明**：指导AI在接口对接过程中的决策逻辑和场景识别

---

## 目录

1. [步骤0：确认上游信息（关键卡点）](#步骤0确认上游信息关键卡点)
2. [接口可配置性判断（必须执行）](#接口可配置性判断必须执行)
3. [步骤1：创建节点决策](#步骤1创建节点决策)
4. [步骤2：参数映射决策](#步骤2参数映射决策)
5. [步骤3：创建工作流决策](#步骤3创建工作流决策)
6. [步骤4：验证与排错](#步骤4验证与排错)
7. [场景识别规则](#场景识别规则)

---

## 步骤0：确认上游信息（关键卡点）

> ⚠️ **此步骤不可跳过！没有上游入参定义，无法配置正确的参数映射。**

### AI智能分析（第一步）

AI自动分析接口文档，识别以下特征：

| 检查项 | 判断依据 | 结论 |
|--------|----------|------|
| 业务关键词 | HIS、收费、PACS、LIS等 | 三方系统接口 |
| 接口协议 | HTTP、WebService、Database | 技术类型确认 |
| 字段命名 | 系统特定前缀（his_、pacs_） | 字段归属确认 |
| 文档来源 | 医院/厂商提供 | 三方系统确认 |

### 上下游关系判断

**核心问题**：谁调用谁？

| 场景 | 上游（调用方） | 下游（被调方） | 典型业务 |
|------|---------------|---------------|----------|
| 场景一 | Jarvis | 三方系统(HIS/PACS) | 建档、账单、申请单、报告查询 |
| 场景二 | 三方系统(HIS/PACS) | Jarvis | 账单通知、报告状态通知 |

**判断依据**：
- 用户说“对接HIS建档接口” → Jarvis调用HIS → **场景一**
- 用户说“接收HIS账单通知” → HIS调用Jarvis → **场景二**
- 用户说“推送报告状态” → HIS/PACS调用Jarvis → **场景二**

### 信息确认策略

**AI应尽量自己判断**，只在以下情况询问用户：

| 情况 | AI行为 |
|------|--------|
| 用户表述清晰（如“对接HIS建档”） | 直接确认并继续 |
| 提供了完整接口文档 | AI分析后确认并继续 |
| 上下游关系不明确 | 询问用户确认 |
| 业务类型无法判断 | 询问用户确认 |
| 缺少上游入参示例 | 要求用户提供 |

**需要确认的信息**（仅在不确定时询问）：
- 上游系统类型：Jarvis / 三方系统 / 其他
- 业务类型：建档 / 账单 / 申请单 / 报告 / 通知回调
- 上游入参来源：引用Jarvis标准 / 自定义入参

### 对话示例

```
AI：我已分析您提供的接口文档：
✓ 识别为：HIS收费系统接口
✓ 接口类型：HTTP POST application/json
✓ 包含接口：创建档案、推送账单、查询状态...

⚠️ 我需要确认上下游关系：
1. 这些接口是 Jarvis调用HIS，还是 HIS调用Jarvis？
2. 如果是Jarvis调用HIS → 是什么业务？（建档/账单/申请单）
3. 如果是HIS调用Jarvis → 请提供HIS的入参示例

用户：Jarvis调用HIS，建档业务

AI：✓ 确认配置信息：
- 上游：Jarvis（引用 jarvis_archive.md）
- 下游：HIS收费系统
- 前置处理：Jarvis标准字段 → HIS字段
- 后置处理：HIS响应 → Jarvis标准响应

开始生成配置...
```

---

## 接口可配置性判断（必须执行）

> ⚠️ **关键检查**：在开始配置前，AI必须先判断接口是否可以通过标准配置处理。

### 可配置性检查清单

**✅ 可以配置化处理的接口**：

| 检查项 | 可配置条件 | 示例 |
|--------|------------|------|
| 协议类型 | HTTP/HTTPS、WebService(SOAP)、Database | RESTful API、WSDL接口、SQL查询 |
| 认证方式 | 无认证、基本认证、静态Token、请求头Header | Basic Auth、`Authorization: Bearer xxx` |
| 数据格式 | JSON、XML、纯文本 | `{"name":"xxx"}`、`<name>xxx</name>` |
| 参数结构 | 标准嵌套结构 | 对象、数组、基本类型 |
| 字段映射 | 字段名直接对应或SpEL表达式 | `patientCode`→`patNo`、`#data=='1'?'男':'女'` |

**❌ 无法配置化处理的接口（需SPI扩展）**：

| 场景 | 原因 | 示例 |
|------|------|------|
| **动态签名** | 需要根据请求内容实时计算签名 | MD5/SHA256签名、HMAC签名 |
| **动态Token** | Token需要先调用登录接口获取 | OAuth2、临时Token |
| **特殊加密** | 参数需要加密/解密处理 | AES加密、RSA加密、Base64编码 |
| **非标准协议** | 自定义协议、TCP长连接 | 私有协议、Socket通信 |
| **复杂业务逻辑** | 需要多步骤交互或复杂计算 | 分步提交、条件跳转、自定义校验 |
| **同名XML标签** | XML中多个同名标签依赖属性区分 | `<item type="A">...</item><item type="B">...</item>` |
| **数组跨源拼接** | 数组元素需要从多个数据源按索引拼接 | `result[0]`来自入参，`result[1]`来自响应 |

### 判断流程

```
分析接口文档
    ↓
检查认证方式
    ├─ 静态Token/Basic Auth? → ✅ 可配置（在metaInfo.headers中配置）
    ├─ 需要动态Token? → ❌ 需SPI扩展
    └─ 需要签名? → ❌ 需SPI扩展
    ↓
检查数据格式
    ├─ JSON/XML? → ✅ 可配置
    └─ 自定义格式? → ❌ 需SPI扩展
    ↓
检查参数结构
    ├─ 标准嵌套结构? → ✅ 可配置
    ├─ 同XML标签依赖属性? → ❌ 需SPI扩展
    └─ 需要加密/解密? → ❌ 需SPI扩展
    ↓
检查业务逻辑
    ├─ 简单字段映射? → ✅ 可配置
    ├─ SpEL表达式可实现? → ✅ 可配置
    └─ 需要复杂计算/查询? → ❌ 需SPI扩展
```

### 处理方式

**如果接口可配置化**：
```
✅ 该接口可以通过标准配置处理，开始创建节点和参数映射...
```

**如果接口不可配置化**：
```
⚠️ 检测到该接口无法通过标准配置处理：

问题：[具体原因，如“需要动态签名”]
解决方案：该接口需要通过SPI扩展实现。

SPI扩展说明：
1. 实现InterfaceRemoteInvoker或NodeParamResolver接口
2. 自定义签名/加密/认证逻辑
3. 通过extensionName指定使用该扩展

建议：请提供更详细的接口文档，我可以帮助识别具体需要哪种SPI扩展。
```

### 常见可配置场景示例

**✅ 可配置：静态Token认证**
```json
"metaInfo": "{
  \"url\": \"http://his.example.com/api\",
  \"headers\": {
    \"Authorization\": \"Bearer fixed_token_12345\",
    \"Content-Type\": \"application/json\"
  }
}"
```

**✅ 可配置：SpEL值转换**
```json
{
  "mappingType": "EXPRESSION",
  "mappingRule": "#data == '1' ? '男' : '女'"
}
```

**❌ 不可配置：动态MD5签名**
```
需求：请求参数 + timestamp + secret 计算MD5
原因：需要在请求前动态计算，SpEL表达式无法实现
解决：实现InterfaceRemoteInvoker扩展
```

---

## 步骤1：创建节点决策

### 节点类型选择

| 接口协议 | 节点类型 | 判断依据 |
|---------|---------|---------|
| RESTful API | HTTP | URL以http://开头，JSON格式 |
| SOAP协议 | WEBSERVICE | 包含WSDL、SOAP信封、XML格式 |
| 数据库直连 | DATABASE | 包含SQL、JDBC连接信息 |

### 关键字段决策

**keywordField选择**：
- 上游是Jarvis → 通常使用 `patientCode` 或 `patientInfo.patientCode`
- 上游是三方系统 → 根据入参中的唯一标识字段选择

**splitField决策**：
- 下游接口支持批量 → 不配置
- 下游接口只支持单条 → 配置数组字段名（如 `items`）

**paramFilterExpr决策**：
- 单节点工作流 → 不配置
- 多节点编排需要路由 → 配置条件表达式

---

## 步骤2：参数映射决策

### mappingType选择决策树

```
对比上游字段和下游字段：
│
├─ 字段名相同，类型一致？
│   └─ 是 → NAME
│
├─ 字段名不同，但含义相同？
│   └─ 是 → NAME（配置不同的sourceParamKey和targetParamKey）
│
├─ 需要值转换？（性别、状态码等）
│   └─ 是 → EXPRESSION
│       └─ 示例：#data == '1' ? '男' : '女'
│
├─ 固定值？（系统标识、常量）
│   └─ 是 → CONSTANT
│       └─ 示例：mappingRule = "JARVIS"
│
├─ 需要调用Bean方法？（数据库查询等）
│   └─ 是 → BEAN_EXPRESSION
│
└─ 嵌套结构？
    ├─ 对象 → 父节点(OBJECT) + childParams
    └─ 数组 → 数组节点(ARRAY) + 虚拟节点 + 元素
```

### processType决策

| 处理阶段 | processType | 数据流向 |
|---------|-------------|---------|
| 构建下游请求 | PRE_PROCESS | 上游入参 → 下游入参 |
| 处理下游响应 | POST_PROCESS | 下游响应 → 上游响应 |

### mappingSource决策（仅后置处理）

| 数据需求 | mappingSource | 说明 |
|---------|---------------|------|
| 从下游响应取值 | RESPONSE | 大多数场景 |
| 从原始入参取值 | INPUT | 下游响应不完整，需补充入参数据 |
| 同时取两边 | 见下文 | 对象拼凑场景 |

**对象拼凑场景**：
- ✅ 支持：同一对象内不同字段使用不同的mappingSource
- ❌ 不支持：数组内按索引拼凑两边数据（需SPI扩展）

### 嵌套结构决策

**判断入参/出参结构**：

```
结构类型判断：
│
├─ 基本类型（String/Integer/Boolean）？
│   └─ 直接配置，无需嵌套
│
├─ 对象类型？
│   └─ 创建父节点(OBJECT) + childParams子节点
│
└─ 数组类型？
    └─ 创建数组节点(ARRAY) + 虚拟节点(OBJECT) + 元素字段
        └─ 虚拟节点命名：xxxNode（如itemNode、orderNode）
```

### 常见字段转换规则

| 字段类型 | Jarvis值 | 常见HIS要求 | 转换表达式 |
|---------|----------|------------|-----------|
| 性别 | "1"/"2" | "男"/"女" | `#data == '1' ? '男' : '女'` |
| 婚姻状态 | "1"/"2" | "未婚"/"已婚" | `#data == '1' ? '未婚' : '已婚'` |
| 证件类型 | "1" | "01" | `#data == '1' ? '01' : #data` |
| 状态码 | "0"/"1" | "200"/"-1" | `#data == '0' ? '200' : '-1'` |
| 国籍 | 可能为空 | "CHN" | 使用CONSTANT固定值 |

---

## 步骤3：创建工作流决策

### interfaceUri决策

| 上游类型 | interfaceUri格式 | 示例 |
|---------|-----------------|------|
| Jarvis是上游 | 使用BusinessMethod | `CreateArchives`、`SyncHisChagSheet` |
| 三方系统是上游 | 使用/agent-open-api/** | `/agent-open-api/charge-notify` |

> **BusinessMethod对照表**：参见 [ai_agent_bootstrap.md](./ai_agent_bootstrap.md) 中的业务场景速查

### contentType决策

| 上游入参格式 | contentType |
|-------------|-------------|
| JSON | JSON |
| XML/SOAP | XML |

### firstFlowNodes决策

| 业务场景 | 节点配置 |
|---------|--------|
| 单一接口调用 | 单节点：`[nodeId]` |
| 需要并行调用多个接口 | 多节点：`[nodeId1, nodeId2]` |
| 需要路由分发 | 多节点 + paramFilterExpr |

---

## 步骤4：验证与排错

### AI自动验证流程（必须执行）

**在完成参数映射配置后，AI必须自动执行以下验证：**

#### 1. 创建Mock配置

使用 `POST /v1/ai/mcp/mock-config/create` 创建测试场景：
- **成功场景**：模拟下游接口返回成功响应
- **mockResponse**：根据下游接口文档构造真实响应格式

#### 2. 执行Mock调用

使用 `POST /v1/ai/mcp/mock-invoke/invoke?nodeId=X&mockId=Y` 模拟真实调用：
- **businessData**：根据上游入参结构构造测试数据
- 框架会执行完整的前置/后置映射流程

#### 3. 查询调用日志

使用 `GET /v1/ai/mcp/invoke-log/latest?limit=1` 获取最新调用日志：
- **businessData**：验证原始输入是否正确
- **paramBeforeInvoke**：验证前置映射是否正确
- **remoteInvokeResponse**：查看Mock响应数据
- **paramAfterInvoke**：验证后置映射是否正确

#### 4. 分析验证结果

**检查项**：
- ✅ 字段名映射是否正确（sourceParamKey → targetParamKey）
- ✅ 值转换是否正确（EXPRESSION的mappingRule）
- ✅ 嵌套结构是否正确（OBJECT/ARRAY的childParams）
- ✅ 默认值是否生效（CONSTANT的mappingRule）
- ✅ 字段过滤是否正确（不需要的字段未传递）

#### 5. 报告验证结果

**如果验证成功**：
```
✅ Mock验证通过！

前置映射验证：
- patientCode → patNo: ✅ 正确
- sex "1" → gender "男": ✅ 正确
- 默认值 nation "CHN": ✅ 正确

后置映射验证：
- returnCode "1" → code "200": ✅ 正确
- errorMessage → message: ✅ 正确
- hisPatientId → rsp.hisPatientId: ✅ 正确

配置已就绪，可以创建工作流。
```

**如果验证失败**：
```
⚠️ Mock验证发现问题：

问题：性别字段转换错误
- 期望：sex "1" → gender "男"
- 实际：sex "1" → gender "1"
- 原因：mappingType配置为NAME，应改为EXPRESSION

正在修复...
```

> ⚠️ **重要**：Mock调用的响应不能直接验证映射正确性，必须查看日志中的转换过程！

### 常见问题排查

| 问题现象 | 可能原因 | 排查方向 |
|---------|---------|---------|
| 调用无响应 | URL错误、网络不通 | 检查节点metaInfo配置 |
| 参数缺失 | 前置映射遗漏 | 检查PRE_PROCESS配置 |
| 响应字段错误 | 后置映射错误 | 检查POST_PROCESS配置 |
| 状态码不对 | 状态码转换表达式错误 | 检查EXPRESSION的mappingRule |
| 数组为空 | 虚拟节点配置错误 | 检查数组的虚拟节点配置 |

---

## 场景识别规则

### 业务场景识别

| 关键词 | 识别为 | 引用文档 |
|-------|-------|---------|
| 建档、创建档案 | 建档业务 | business/archive_creation.md |
| 档案查询 | 档案查询业务 | business/archive_query.md |
| 档案修改 | 档案修改业务 | business/archive_update.md |
| 申请单 | 申请单业务 | business/apply_creation.md |
| 账单创建、收费 | 账单创建业务 | business/charge_order_creation.md |
| 账单删除 | 账单删除业务 | business/charge_order_deletion.md |
| 账单拟退 | 账单拟退业务 | business/charge_order_revocation.md |
| 拟退取消 | 拟退取消业务 | business/charge_order_revocation_cancellation.md |
| 支付状态查询 | 支付状态查询 | business/charge_payment_status_query.md |
| 退费状态查询 | 退费状态查询 | business/charge_refund_status_query.md |
| 收费通知、支付通知 | 收费通知业务 | business/charge_payment_notification.md |
| 退费通知 | 退费通知业务 | business/charge_refund_notification.md |
| 报告状态推送 | 报告状态推送 | business/report_status.md |
| PACS报告获取、影像报告 | PACS报告查询 | business/report_pacs_query.md |
| LIS报告获取、检验报告 | LIS报告查询 | business/report_lis_query.md |

