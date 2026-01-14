---
trigger: always_on
---

# Interface-Agent AI配置指南

> **本文档是AI理解Agent接口编排的核心入口，指导如何配置不同业务场景的接口对接**

---

## 快速导航

```
AI配置流程
│
步骤1：理解框架规则
    → project_knowledge.md（必读：映射类型、参数类型、数据结构等核心概念）
│
步骤2：决策配置方案
    → decision_guide.md（决策指南：根据业务场景选择映射方式）
    → business/*.md（业务专业指南：参考具体业务的配置示例）
│
步骤3：执行配置操作
    → operation_skills.md（操作手册：调用MCP接口完成配置）
```

---

## 知识库结构

| 文档 | 定位 | 使用时机 |
|------|------|----------|
| [project_knowledge.md](./project_knowledge.md) | **What - 框架规则** | **必读**：AI开始配置前必须理解的核心概念（映射类型、参数类型、虚拟节点等） |
| [decision_guide.md](./decision_guide.md) | **Why - 决策指南** | 根据业务需求判断：选择哪种映射类型、如何处理数组、如何转换字段 |
| [business/*.md](./business/) | **业务专业指南** | 参考具体业务场景的完整配置示例（档案、账单、通知等） |
| [operation_skills.md](./operation_skills.md) | **How - 操作手册** | 执行配置：调用MCP接口创建节点、配置映射、创建工作流 |

---

## 配置流程总览

```
步骤0：确认上下游信息
    ↓
步骤1：创建节点 → operation_skills.md#节点操作
    ↓
步骤2：配置参数映射 → operation_skills.md#参数映射操作
    ↓
步骤3：创建工作流 → operation_skills.md#工作流操作
    ↓
步骤4：验证配置
```

---

## 业务场景速查

### 档案类接口（上游：Jarvis → 下游：HIS）

| 业务 | BusinessMethod | 参考文档 |
|------|---------------|----------|
| 个人建档 | CreateArchives | [archive_creation.md](./business/archive_creation.md) |
| 查询档案 | QueryArchives | [query_archive.md](./business/query_archive.md) |

### 账单类接口（上游：Jarvis → 下游：HIS）

| 业务 | BusinessMethod | 参考文档 |
|------|---------------|----------|
| 账单创建 | SyncHisChagSheet | [charge_order_creation.md](./business/charge_order_creation.md) |
| 账单删除 | DeleteHisChag | [charge_order_deletion.md](./business/charge_order_deletion.md) |
| 账单拟退 | RevokeChag | [charge_order_revocation.md](./business/charge_order_revocation.md) |
| 取消拟退 | CancelRevokeChag | [charge_order_revocation_cancellation.md](./business/charge_order_revocation_cancellation.md) |
| 支付状态查询 | QueryChagPay | [charge_payment_status_query.md](./business/charge_payment_status_query.md) |
| 退费状态查询 | QueryChagRefund | [charge_refund_status_query.md](./business/charge_refund_status_query.md) |

### 通知类接口（上游：HIS → 下游：Jarvis）

| 业务 | interfaceUri | 参考文档 |
|------|-------------|---------|
| 账单收费通知 | /agent-open-api/charge-notify | [charge_payment_notification.md](./business/charge_payment_notification.md) |
| 账单退费通知 | /agent-open-api/refund-notify | [charge_refund_notification.md](./business/charge_refund_notification.md) |

### 申请单类接口（上游：Jarvis → 下游：HIS/LIS/PACS）

| 业务 | BusinessMethod | 参考文档 |
|------|---------------|----------|
| 申请单创建 | SyncPacsLisNewApply | 待补充 |
| 报告查询 | GetItemResult | 待补充 |

---

## 对接工作检查清单

```
☐ 确认上下游关系（谁调用谁）
☐ 获取下游接口文档
☐ 确认上游入参结构
☐ 创建节点并配置metaInfo
☐ 配置前置处理（上游入参 → 下游入参）
☐ 配置后置处理（下游响应 → 上游响应）
☐ 创建工作流并关联节点
☐ 使用export接口验证配置
```
