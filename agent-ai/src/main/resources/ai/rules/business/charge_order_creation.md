# 账单创建接口配置指南

> 本文档为账单创建场景的**业务专用指南**，配置规则详见 `ai_config_guide.md`

## 场景识别

当客户提到「创建账单」「推送收费」「开单接口」时，明确为账单创建场景。

---

## 业务要点（必读）

### 1. 账单模式差异

不同his厂商设计不同，**AI需根据his文档判断模式**：

| 模式 | 说明 | Jarvis字段 |
|------|------|------------|
| **总价模式** | his只需账单总金额 | `chagPatientSheet.sumAmount`，无需明细 |
| **明细模式** | his需要收费项列表 | `chagPatientSheetDetail`，无需物价 |
| **物价模式** | his需要到物价级别 | `chagPatientSheetDetail` + `consumeList` |

**判断规则**：
- his入参有明细数组 → 需要明细
- 明细中有物价子数组 → 需要物价
- 只有总金额 → 总价模式

### 2. 幂等字段（必须关注）

**关键字段**：`chagPatientSheet.idChagSync`

**作用**：体检系统生成的账单版本号，his需存储并做幂等判断。

**AI处理**：
1. 检查his入参是否有幂等ID字段（如`orderId`、`billNo`）
2. 有 → 将`idChagSync`映射到该字段
3. 没有 → **必须提示客户**确认his是否支持幂等控制

### 3. 常见字段差异

| 业务点 | 差异 |
|--------|------|
| 性别 | "1/2" 或 "男/女" |
| 金额 | 分 或 元 |
| 状态码 | "1"、"0"、"200" |

---

## Jarvis固定结构

### 入参（字段名固定）

```json
{
  "chargeMode": "cash",
  "doctorInfo": { "idUser": 22, "userName": "xxx", "number": "xxk" },
  "patientInfo": {
    "patientName": "测试", "hisPatientId": "030001375", "visitNo": "20250908000204"
  },
  "chagPatientSheet": {
    "idChagSync": "幂等ID", "idPatientSheet": 2484, "sumAmount": 17000, "sumAmountYuan": 170
  },
  "chagPatientSheetDetail": [
    {
      "idPatientFeeItem": 45857, "feeItemName": "B超", "factPriceYuan": 80,
      "consumeList": [{ "idConsumeItem": 510098, "factPriceYuan": 80 }]
    }
  ]
}
```

**关键字段**：
- `patientInfo.hisPatientId` - his客户档案ID
- `patientInfo.visitNo` - his门诊号
- `chagPatientSheet.idChagSync` - 幂等ID（必须映射）
- `chagPatientSheetDetail` - 明细数组
- `consumeList` - 物价数组

### 响应（字段名固定）

```json
{
  "code": "200",
  "message": "成功",
  "rsp": {
    "presId": "his账单ID",
    "hisItems": [{ "idPatientFeeItem": "45857", "hisDetailId": "his明细ID" }]
  }
}
```

---

## AI配置步骤

### 步骤1：获取his接口信息

**如客户已提供** → 直接使用，无需询问
**如未提供** → 询问：
1. his创建账单接口的入参字段？
2. his响应中账单ID和明细ID的字段名？
3. 是否有固定值字段（账单类型、科室ID）？

### 步骤2：生成前置映射（Jarvis → his）

- 直接映射：`patientInfo.hisPatientId` → `patientId`（EXPRESSION: `#data[hisPatientId]`）
- 固定值：`orderType` = `"1"`（CONSTANT）
- 数组嵌套：`chagPatientSheetDetail` → `orderDetails`（带childParam）

### 步骤3：生成后置映射（his → Jarvis）

- `his账单ID字段` → `rsp.presId`
- `his明细ID数组` → `rsp.hisItems`（数组映射）
- 状态码转换：`returnCode == "1" ? "200" : "-1"`（EXPRESSION）
