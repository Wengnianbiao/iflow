---
trigger: always_on
---

# Interface-Agent 配置入口

> **本文档是AI配置工作的入口索引，请按需查阅对应文档**

---

## 快速导航

```
你想做什么？
│
├── 了解项目 → project_knowledge.md
│
├── 开始配置 → decision_guide.md（决策） + operation_skills.md（操作）
│
└── 参考案例 → business/*.md
```

---

## 知识库结构

| 文档 | 定位 | 何时查阅 |
|------|------|---------|
| [project_knowledge.md](./project_knowledge.md) | What - 框架概念 | 需要了解配置规范、字段含义、数据类型时 |
| [decision_guide.md](./decision_guide.md) | When & Why - 决策指南 | 需要判断如何配置、选择哪种映射类型时 |
| [operation_skills.md](./operation_skills.md) | How - 操作步骤 | 需要调用MCP接口、执行具体操作时 |
| [business/*.md](./business/) | 业务案例 | 需要参考具体业务配置示例时 |

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

### 上游是Jarvis的场景

| 业务 | BusinessMethod | 参考文档 |
|------|---------------|---------|
| 个人建档 | CreateArchives | [archive_creation.md](./business/archive_creation.md) |
| 账单创建 | SyncHisChagSheet | [charge_order_creation.md](./business/charge_order_creation.md) |
| 申请单创建 | SyncPacsLisNewApply | - |
| 报告查询 | GetItemResult | - |

### 上游是三方系统的场景

| 业务 | interfaceUri | 参考文档 |
|------|-------------|---------|
| 账单通知 | /agent-open-api/charge-notify | - |
| 报告状态通知 | /agent-open-api/report-status | - |

---

## 常用决策速查

### mappingType选择

| 场景 | mappingType | 示例 |
|------|-------------|------|
| 字段名不同，直接取值 | NAME | patientCode → patNo |
| 固定值 | CONSTANT | source = "JARVIS" |
| 需要转换/计算 | EXPRESSION | sex转gender |
| 调用Spring Bean | BEAN_EXPRESSION | 复杂业务逻辑 |

### 后置处理mappingSource选择

| 数据来源 | mappingSource |
|---------|--------------|
| 从下游响应取值 | RESPONSE |
| 从原始上游入参取值 | INPUT |

---

## 注意事项

1. **数组映射必须包含虚拟节点**
2. **数组中无法同时依赖INPUT和RESPONSE**
3. **keywordField通常使用patientCode（上游是Jarvis时）**
4. **metaInfo是JSON字符串，需要转义**

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
