---
trigger: always_on
---
# AI决策指南

> **本文档说明**：指导AI在接口对接过程中的决策逻辑和场景识别

---

## 目录

1. [步骤0：确认上游信息（关键卡点）](#步骤0确认上游信息关键卡点)
2. [步骤1：创建节点决策](#步骤1创建节点决策)
3. [步骤2：参数映射决策](#步骤2参数映射决策)
4. [步骤3：创建工作流决策](#步骤3创建工作流决策)
5. [步骤4：验证与排错](#步骤4验证与排错)
6. [场景识别规则](#场景识别规则)

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

### 验证流程

1. **关闭Mock开关**（如有）
2. **上游发起真实调用**
3. **查看调用日志**
4. **检查返回结果**

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

### 接口类型识别

| 特征 | 识别为 |
|-----|-------|
| http://xxx、POST/GET、JSON | HTTP接口 |
| WSDL、SOAP、Envelope、XML | WebService接口 |
| JDBC、SQL、SELECT/INSERT | Database接口 |

### 上下游角色识别

| 用户表述 | 上游 | 下游 |
|---------|-----|-----|
| "对接HIS建档接口" | Jarvis | HIS |
| "调用收费系统" | Jarvis | 收费系统 |
| "接收HIS通知" | HIS | Jarvis |
| "HIS推送账单状态" | HIS | Jarvis |
| "PACS回传报告" | PACS | Jarvis |
