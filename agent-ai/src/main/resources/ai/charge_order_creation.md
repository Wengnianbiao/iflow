 账单创建接口配置指南

## AI配置任务

**场景识别**：当客户提到"创建账单"、"推送收费"、"开单接口"时，明确为账单创建场景。

**关键认知**：
- **上游Jarvis入参**：字段名固定不变，值随业务变化
- **下游his系统**：每个厂商接口字段名不同，需客户提供文档或接口的入参和响应
- **配置目标**：将Jarvis固定字段名映射到厂商his字段名，再将his响应字段映射回Jarvis固定字段名
- **协议无关**：框架已通过Map屏蔽JSON/XML差异，只关注字段映射

## 数据流转链路

```
Jarvis入参（字段名固定）→ [前置映射] → 厂商his入参（字段名待配置）→ his系统 → his响应（字段名待配置）→ [后置映射] → Jarvis响应（字段名固定）
```

**AI需要做的**：
1. 从提供的his接口文档或出入参提取字段
2. 将固定的Jarvis字段映射到his字段（前置处理）
3. 将his响应字段映射回Jarvis固定响应（后置处理）

---

## 1. Jarvis入参（上游输入，字段名固定）

**AI注意**：字段名固定不变，值随业务数据变化。

```json
{
  "chargeMode": "cash",
  "doctorInfo": {
    "idUser": 22,
    "userName": "信息科(勿删)",
    "number": "xxk"
  },
  "patientInfo": {
    "sex": "1",
    "age": 35,
    "patientName": "测试账号",
    "patientCode": "102509080001",
    "hisPatientId": "030001375",
    "visitNo": "20250908000204",
    "phone": "17826809454"
  },
  "chagPatientSheet": {
    "idChagSync":"jarvis生成的账单版本号",
    "idPatientSheet": 2484,
    "settlementOrderNo": "2484",
    "sumAmount": 17000,
    "sumAmountYuan": 170
  },
  "chagPatientSheetDetail": [
    {
      "idPatientFeeItem": 45857,
      "price": 8000,
      "factPrice": 8000,
      "factPriceYuan": 80,
      "dfiInterfaceCodeName1": "收费编码",
      "feeItemName": "B超(肾输尿管膀胱)",
      "departCode": "5",
      "consumeList": [
        {
          "dciName": "B超(肾输尿管膀胱)",
          "idConsumeItem": 510098,
          "factPrice": "8000.00",
          "quantity": 1,
          "factPriceYuan": 80
        }
      ]
    }
  ]
}
```

**关键字段说明**：
- `patientInfo.hisPatientId`：his系统的客户档案ID
- `patientInfo.visitNo`：his系统的门诊号
- `chagPatientSheet.idPatientSheet`：体检系统的账单ID
- `chagPatientSheetDetail`：账单明细数组，包含收费项信息
- `consumeList`：每个收费项包含的物价明细列表

---

## 2. Jarvis响应（最终输出，字段名固定）

**AI注意**：必须将厂商his响应字段映射为此字段名，无论厂商his返回什么字段名。

```json
{
  "code": "200",
  "message": "成功",
  "rsp": {
    "presId": "his系统返回的账单ID",
    "hisItems": [
      {
        "idPatientFeeItem": "45857",
        "hisDetailId": "his系统返回的明细ID"
      }
    ]
  }
}
```

**关键字段说明**：
- `code`：状态码，200表示成功
- `rsp.presId`：his系统创建的账单主键ID
- `rsp.hisItems`：his账单明细ID与体检系统明细ID的映射关系

---

## 3. 厂商his入参（需客户提供，字段名不定）

**AI提问模板**：
> "请提供his厂商创建账单接口的入参字段清单，包括：字段名、字段类型、是否必填、字段说明。"

**示例格式**（仅供参考，实际需客户提供）：

```json
{
  "patientId": "客户ID",
  "clinicId": "门诊号",
  "patientCode": "体检编号",
  "orderId": "体检系统账单id",
  "name": "张三",
  "age": "28",
  "sex": "男",
  "orderType": "1",
  "sumAmount": "10000",
  "sumAmountYuan": "100",
  "operatorId": "操作员ID",
  "orderDeptId": "开单部门ID",
  "orderDetails": [
    {
      "orderDetailId": "体检系统账单明细ID",
      "itemName": "收费项名称",
      "itemId": "收费项id",
      "itemPrice": "价格",
      "itemFactPrice": "收费金额,单位元",
      "itemDepartCode": "收费项所属科室id",
      "consumeList": [
        {
          "consumeId": "物价id",
          "quantity": "物价数量",
          "factPrice": "物价价格，分",
          "factPriceYuan": "物价价格，元"
        }
      ]
    }
  ]
}
```

**映射关系示意**：
- `patientId` ← `patientInfo.hisPatientId`
- `clinicId` ← `patientInfo.visitNo`
- `name` ← `patientInfo.patientName`
- `orderId` ← `chagPatientSheet.idPatientSheet`
- `orderDetails` ← `chagPatientSheetDetail`（数组映射）

---

## 4. 厂商his响应（需客户提供，字段名不定）

**AI提问模板**：
> "请提供his创建账单接口的响应字段清单，特别需要：账单ID字段名、明细ID字段名。"

**示例格式**（仅供参考，实际需客户提供）:

```json
{
  "returnCode": "1",
  "errorMessage": "成功",
  "data": {
    "hisOrderId": "his账单ID",
    "orderId": "体检系统账单ID",
    "hisOrderDetails": [
      {
        "hisOrderDetailId": "his明细ID",
        "orderDetailId": "体检系统明细ID"
      }
    ]
  }
}
```

**映射关系示意**：
- `code` ← `returnCode == "1" ? "200" : "-1"`（表达式转换）
- `message` ← `errorMessage`
- `rsp.presId` ← `data.hisOrderId`
- `rsp.hisItems` ← `data.hisOrderDetails`（数组映射）

---

## 5. 完整参数映射配置

```json
{
  "nodeInfo": {
    "nodeName": "创建账单",
    "nodeType": "HTTP"
  },
  "paramsConfig": [
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "patientInfo",
      "sourceParamType": "OBJECT",
      "paramDesc": "his客户档案号",
      "targetParamKey": "patientId",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "EXPRESSION",
      "mappingSource": null,
      "mappingRule": "#data[hisPatientId]",
      "childParam": null
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "patientInfo",
      "sourceParamType": "OBJECT",
      "paramDesc": "his门诊号",
      "targetParamKey": "clinicId",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "EXPRESSION",
      "mappingSource": null,
      "mappingRule": "#data[visitNo]",
      "childParam": null
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "patientInfo",
      "sourceParamType": "OBJECT",
      "paramDesc": "姓名",
      "targetParamKey": "name",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "EXPRESSION",
      "mappingSource": null,
      "mappingRule": "#data[patientName]",
      "childParam": null
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "patientInfo",
      "sourceParamType": "OBJECT",
      "paramDesc": "年龄",
      "targetParamKey": "age",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "EXPRESSION",
      "mappingSource": null,
      "mappingRule": "#data[age]",
      "childParam": null
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "patientInfo",
      "sourceParamType": "OBJECT",
      "paramDesc": "性别",
      "targetParamKey": "sex",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "EXPRESSION",
      "mappingSource": null,
      "mappingRule": "#data[sex]",
      "childParam": null
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": null,
      "sourceParamType": "NONE",
      "paramDesc": "账单类型固定为1",
      "targetParamKey": "orderType",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "CONSTANT",
      "mappingSource": null,
      "mappingRule": "1",
      "childParam": null
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "chagPatientSheet",
      "sourceParamType": "OBJECT",
      "paramDesc": "总价（元）",
      "targetParamKey": "sumAmountYuan",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "EXPRESSION",
      "mappingSource": null,
      "mappingRule": "#data[sumAmountYuan]",
      "childParam": null
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "chagPatientSheet",
      "sourceParamType": "OBJECT",
      "paramDesc": "体检系统账单幂等id",
      "targetParamKey": "orderId",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "EXPRESSION",
      "mappingSource": null,
      "mappingRule": "#data[idChagSync]",
      "childParam": null
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "doctorInfo",
      "sourceParamType": "OBJECT",
      "paramDesc": "开单医生",
      "targetParamKey": "operatorId",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "EXPRESSION",
      "mappingSource": null,
      "mappingRule": "#data[number]",
      "childParam": null
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": null,
      "sourceParamType": "NONE",
      "paramDesc": "开单科室，固定健康管理科",
      "targetParamKey": "orderDeptId",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "CONSTANT",
      "mappingSource": null,
      "mappingRule": "21",
      "childParam": null
    },
    {
      "processType": "PRE_PROCESS",
      "sourceParamKey": "chagPatientSheetDetail",
      "sourceParamType": "ARRAY",
      "paramDesc": "收费项明细",
      "targetParamKey": "orderDetails",
      "targetParamType": "ARRAY",
      "sort": 1,
      "mappingType": "NAME",
      "mappingSource": null,
      "mappingRule": null,
      "childParam": [
        {
          "processType": "PRE_PROCESS",
          "sourceParamKey": null,
          "sourceParamType": "NONE",
          "paramDesc": "收费项明细虚拟节点",
          "targetParamKey": "orderDetailsNode",
          "targetParamType": "OBJECT",
          "sort": 1,
          "mappingType": "NAME",
          "mappingSource": null,
          "mappingRule": null,
          "childParam": [
            {
              "processType": "PRE_PROCESS",
              "sourceParamKey": "idPatientFeeItem",
              "sourceParamType": "STRING",
              "paramDesc": "收费项明细id",
              "targetParamKey": "orderDetailId",
              "targetParamType": "STRING",
              "sort": 1,
              "mappingType": "NAME",
              "mappingSource": null,
              "mappingRule": null,
              "childParam": null
            },
            {
              "processType": "PRE_PROCESS",
              "sourceParamKey": "feeItemName",
              "sourceParamType": "STRING",
              "paramDesc": "收费项名称",
              "targetParamKey": "itemName",
              "targetParamType": "STRING",
              "sort": 1,
              "mappingType": "NAME",
              "mappingSource": null,
              "mappingRule": null,
              "childParam": null
            },
            {
              "processType": "PRE_PROCESS",
              "sourceParamKey": "dfiInterfaceCodeName1",
              "sourceParamType": "STRING",
              "paramDesc": "收费项id",
              "targetParamKey": "itemId",
              "targetParamType": "STRING",
              "sort": 1,
              "mappingType": "NAME",
              "mappingSource": null,
              "mappingRule": null,
              "childParam": null
            },
            {
              "processType": "PRE_PROCESS",
              "sourceParamKey": "factPriceYuan",
              "sourceParamType": "STRING",
              "paramDesc": "收费项实际收费价格",
              "targetParamKey": "itemFactPrice",
              "targetParamType": "STRING",
              "sort": 1,
              "mappingType": "NAME",
              "mappingSource": null,
              "mappingRule": null,
              "childParam": null
            },
            {
              "processType": "PRE_PROCESS",
              "sourceParamKey": "price",
              "sourceParamType": "STRING",
              "paramDesc": "收费项原价",
              "targetParamKey": "itemPrice",
              "targetParamType": "STRING",
              "sort": 1,
              "mappingType": "NAME",
              "mappingSource": null,
              "mappingRule": null,
              "childParam": null
            },
            {
              "processType": "PRE_PROCESS",
              "sourceParamKey": "departCode",
              "sourceParamType": "STRING",
              "paramDesc": "收费项归属科室",
              "targetParamKey": "itemDepartCode",
              "targetParamType": "STRING",
              "sort": 1,
              "mappingType": "NAME",
              "mappingSource": null,
              "mappingRule": null,
              "childParam": null
            },
            {
              "processType": "PRE_PROCESS",
              "sourceParamKey": "consumeList",
              "sourceParamType": "ARRAY",
              "paramDesc": "物价明细列表",
              "targetParamKey": "consumeList",
              "targetParamType": "ARRAY",
              "sort": 1,
              "mappingType": "NAME",
              "mappingSource": null,
              "mappingRule": null,
              "childParam": [
                {
                  "processType": "PRE_PROCESS",
                  "sourceParamKey": null,
                  "sourceParamType": "NONE",
                  "paramDesc": "物价明细虚拟节点",
                  "targetParamKey": "consumeNode",
                  "targetParamType": "OBJECT",
                  "sort": 1,
                  "mappingType": "NAME",
                  "mappingSource": null,
                  "mappingRule": null,
                  "childParam": [
                    {
                      "processType": "PRE_PROCESS",
                      "sourceParamKey": "idConsumeItem",
                      "sourceParamType": "STRING",
                      "paramDesc": "物价id",
                      "targetParamKey": "consumeId",
                      "targetParamType": "STRING",
                      "sort": 1,
                      "mappingType": "NAME",
                      "mappingSource": null,
                      "mappingRule": null,
                      "childParam": null
                    },
                    {
                      "processType": "PRE_PROCESS",
                      "sourceParamKey": "quantity",
                      "sourceParamType": "INTEGER",
                      "paramDesc": "物价数量",
                      "targetParamKey": "quantity",
                      "targetParamType": "STRING",
                      "sort": 1,
                      "mappingType": "NAME",
                      "mappingSource": null,
                      "mappingRule": null,
                      "childParam": null
                    },
                    {
                      "processType": "PRE_PROCESS",
                      "sourceParamKey": "factPrice",
                      "sourceParamType": "STRING",
                      "paramDesc": "物价价格（分）",
                      "targetParamKey": "factPrice",
                      "targetParamType": "STRING",
                      "sort": 1,
                      "mappingType": "NAME",
                      "mappingSource": null,
                      "mappingRule": null,
                      "childParam": null
                    },
                    {
                      "processType": "PRE_PROCESS",
                      "sourceParamKey": "factPriceYuan",
                      "sourceParamType": "INTEGER",
                      "paramDesc": "物价价格（元）",
                      "targetParamKey": "factPriceYuan",
                      "targetParamType": "STRING",
                      "sort": 1,
                      "mappingType": "NAME",
                      "mappingSource": null,
                      "mappingRule": null,
                      "childParam": null
                    }
                  ]
                }
              ]
            }
          ]
        }
      ]
    },
    {
      "processType": "POST_PROCESS",
      "sourceParamKey": "returnCode",
      "sourceParamType": "STRING",
      "paramDesc": "响应状态码转换：1->200，其他->-1",
      "targetParamKey": "code",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "EXPRESSION",
      "mappingSource": "RESPONSE",
      "mappingRule": "#data == \"1\" ? \"200\" : \"-1\"",
      "childParam": null
    },
    {
      "processType": "POST_PROCESS",
      "sourceParamKey": "errorMessage",
      "sourceParamType": "STRING",
      "paramDesc": "响应消息",
      "targetParamKey": "message",
      "targetParamType": "STRING",
      "sort": 1,
      "mappingType": "NAME",
      "mappingSource": "RESPONSE",
      "mappingRule": null,
      "childParam": null
    },
    {
      "processType": "POST_PROCESS",
      "sourceParamKey": "data",
      "sourceParamType": "OBJECT",
      "paramDesc": "响应数据对象",
      "targetParamKey": "rsp",
      "targetParamType": "OBJECT",
      "sort": 1,
      "mappingType": "NAME",
      "mappingSource": "RESPONSE",
      "mappingRule": null,
      "childParam": [
        {
          "processType": "POST_PROCESS",
          "sourceParamKey": "hisOrderId",
          "sourceParamType": "STRING",
          "paramDesc": "his账单ID",
          "targetParamKey": "presId",
          "targetParamType": "STRING",
          "sort": 1,
          "mappingType": "NAME",
          "mappingSource": "RESPONSE",
          "mappingRule": null,
          "childParam": null
        },
        {
          "processType": "POST_PROCESS",
          "sourceParamKey": "hisOrderDetails",
          "sourceParamType": "ARRAY",
          "paramDesc": "his账单明细列表",
          "targetParamKey": "hisItems",
          "targetParamType": "ARRAY",
          "sort": 1,
          "mappingType": "NAME",
          "mappingSource": "RESPONSE",
          "mappingRule": null,
          "childParam": [
            {
              "processType": "POST_PROCESS",
              "sourceParamKey": null,
              "sourceParamType": "NONE",
              "paramDesc": "明细映射虚拟节点",
              "targetParamKey": "hisItemsNode",
              "targetParamType": "OBJECT",
              "sort": 1,
              "mappingType": "NAME",
              "mappingSource": "RESPONSE",
              "mappingRule": null,
              "childParam": [
                {
                  "processType": "POST_PROCESS",
                  "sourceParamKey": "hisOrderDetailId",
                  "sourceParamType": "STRING",
                  "paramDesc": "his收费项明细ID",
                  "targetParamKey": "hisDetailId",
                  "targetParamType": "STRING",
                  "sort": 1,
                  "mappingType": "NAME",
                  "mappingSource": "RESPONSE",
                  "mappingRule": null,
                  "childParam": null
                },
                {
                  "processType": "POST_PROCESS",
                  "sourceParamKey": "orderDetailId",
                  "sourceParamType": "STRING",
                  "paramDesc": "体检系统收费项明细ID",
                  "targetParamKey": "idPatientFeeItem",
                  "targetParamType": "STRING",
                  "sort": 1,
                  "mappingType": "NAME",
                  "mappingSource": "RESPONSE",
                  "mappingRule": null,
                  "childParam": null
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

---

## 业务要点说明

### 账单模式差异（根据his文档确定）

不同his厂商的账单接口设计不同，需根据具体文档判断：

| 模式 | 说明 | 需要的Jarvis字段 |
|------|------|------------------|
| **总价模式** | his只需账单总金额 | `chagPatientSheet.sumAmount`，不需要`chagPatientSheetDetail` |
| **明细模式** | his需要收费项列表 | `chagPatientSheetDetail`，不需要`consumeList` |
| **物价模式** | his需要到物价级别 | `chagPatientSheetDetail` + 每项的`consumeList` |

**AI判断规则**：
- 查看his入参是否有明细数组字段 → 有则需要明细
- 明细中是否有物价子数组 → 有则需要物价
- 只有总金额字段 → 总价模式

### 常见业务字段差异

| 业务点 | 可能的差异 |
|--------|------------|
| 性别 | 有的用"1/2"，有的用"男/女" |
| 金额单位 | 有的用分，有的用元 |
| 状态码 | 有的用"1"表示成功，有的用"0"或"200" |
| 日期格式 | 有的用时间戳，有的用字符串 |

### 幂等字段（必须关注）

**关键字段**：`chagPatientSheet.idChagSync`

**作用**：
- 这是体检系统生成的账单版本号，需要传给his厂商存储
- his厂商需基于该字段做幂等判断，防止重复创建账单

**AI处理规则**：
1. 检查his入参是否有对应的幂等ID字段（如`orderId`、`billNo`等）
2. 如果有 → 将`idChagSync`映射到该字段
3. 如果没有 → **必须提示客户**：
   > "创建账单接口需要幂等控制，请确认his厂商是否支持存储账单版本号字段（idChagSync），并基于该字段做重复判断。"

### 前置处理配置要点

1. **对象字段提取**：使用EXPRESSION映射类型
   - 示例：`patientInfo.hisPatientId` → `patientId`
   - mappingRule: `#data[hisPatientId]`

2. **固定值配置**：使用CONSTANT映射类型
   - 示例：`orderType` 固定为 `"1"`
   - mappingRule: `"1"`

3. **数组嵌套映射**：多层childParam
   - `chagPatientSheetDetail` → `orderDetails`（一级数组）
     - 虚拟节点 `orderDetailsNode`
       - `consumeList` → `consumeList`（二级数组）
         - 虚拟节点 `consumeNode`

### 后置处理配置要点

1. **状态码转换**：使用EXPRESSION
   - mappingRule: `#data == "1" ? "200" : "-1"`

2. **对象嵌套映射**：
   - `data` → `rsp`（父对象）
     - `hisOrderId` → `presId`
     - `hisOrderDetails` → `hisItems`（数组）

3. **数组元素映射**：通过虚拟节点
   - 虚拟节点 `hisItemsNode`
     - `hisOrderDetailId` → `hisDetailId`
     - `orderDetailId` → `idPatientFeeItem`

---

## AI配置步骤

### 步骤1：明确场景
- 确认上游是Jarvis（入参固定）
- 确认功能是创建his账单

### 步骤2：获取his接口信息
**判断逻辑**：
- 如果客户已提供his入参/出参/文档 → 直接使用，无需询问
- 如果未提供 → 询问以下问题：
  1. "his创建账单接口的入参字段有哪些？"
  2. "his响应中账单ID和明细ID的字段名是什么？"
  3. "是否有固定值字段（如账单类型、科室ID）？"

### 步骤3：生成前置映射（Jarvis → his）
**映射规则**：
- 直接字段：`patientInfo.hisPatientId` → `patientId`（EXPRESSION: `#data[hisPatientId]`）
- 固定值：`orderType` = `"1"`（CONSTANT）
- 数组嵌套：`chagPatientSheetDetail` → `orderDetails`（带childParam）

### 步骤4：生成后置映射（his → Jarvis）
**核心映射**：
- `his账单ID字段` → `rsp.presId`
- `his明细ID数组` → `rsp.hisItems`（数组映射）
- 状态码转换：`returnCode == "1" ? "200" : "-1"`（EXPRESSION）

### 步骤5：处理特殊场景
- **多层数组**：收费项下包含物价明细（二级嵌套）
- **虚拟节点**：数组映射时需要虚拟对象节点包裹字段
- **类型转换**：his可能返回数字，需转为字符串
