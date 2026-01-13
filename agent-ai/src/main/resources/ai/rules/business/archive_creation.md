# 档案创建接口配置指南

> 本文档为档案创建（建档）场景的**业务专用指南**，配置规则详见 `ai_config_guide.md`

## 场景识别

当客户提到「建档」「创建档案」「患者登记」「挂号建档」时，明确为档案创建场景。

---

## 业务要点（必读）

### 1. 接口协议差异

his厂商接口可能是JSON或XML格式，**框架已通过Map屏蔽差异**，只关注字段映射。

| 协议 | 特点 | 配置差异 |
|------|------|----------|
| **HTTP/JSON** | 扁平结构 | 直接映射字段 |
| **WebService/XML** | 嵌套结构（Head/Body） | 需配置XML标签层级 |

### 2. 常见字段转换

建档接口常见的值转换场景：

| 字段 | Jarvis值 | his可能要求 | 转换示例 |
|------|----------|-------------|----------|
| 性别 | "1"/"2" | "男"/"女" | `#data == '1' ? '男' : '女'` |
| 婚姻 | "1"/"2" | "未婚"/"已婚" | `#data == '1' ? '未婚' : '已婚'` |
| 证件类型 | "1" | "0" | `#data == "1" ? "0" : "1"` |
| 体检类型 | 0/1 | "1"/"2" | `#data == 0 ? "1" : "2"` |

### 3. 嵌套字段提取

Jarvis入参中部分字段在嵌套对象内，需用EXPRESSION提取：

| 目标 | 来源 | 映射规则 |
|------|------|----------|
| 操作员ID | `doctorInfo.number` | `#data[number]` |
| 操作员姓名 | `doctorInfo.userName` | `#data[userName]` |
| 登记时间 | `patientInfo.registertime` | `#data[registertime]` |
| 单位ID | `patientInfo.hisOrgId` | `#data[hisOrgId]` |

### 4. XML协议特殊处理

WebService接口通常需要配置固定的Head信息：

```
HrRequest
├── Head（固定值）
│   ├── TradeCode: "PAT202"（交易码）
│   ├── SystemCode: "TJ"（系统编码）
│   ├── BranchCode: "01"（院区）
│   └── HipKeyNo: "xxx"（认证串，现场提供）
└── Body（业务数据）
    └── 实际业务字段...
```

**AI处理**：询问客户Head中的固定值（TradeCode、认证串等）

---

## Jarvis固定结构

### 入参（字段名固定）

```json
{
  "patientName": "测试账号",
  "patientCode": "2509050001",
  "sex": "2",
  "age": 53,
  "birthday": "1972-02-25",
  "identitycard": "330511197202254629",
  "cardType": "1",
  "phone": "13059970500",
  "address": "湖州",
  "marriage": "2",
  "nation": "01",
  "nationName": "汉族",
  "orgName": "测试单位",
  "hisPatientId": null,
  "visitNo": null,
  "archivesCode": "10011081",
  "archivalNo": "10011081",
  "userType": 1,
  "patientInfo": {
    "hisOrgId": "85330",
    "registertime": "2025-08-19 08:28:01",
    "physicalTypes": "physical:types:A"
  },
  "doctorInfo": {
    "idUser": 705,
    "userName": "陈元梅",
    "number": "cym0555"
  }
}
```

**关键字段**：
- `patientCode` - 体检编号
- `identitycard` - 身份证号
- `hisPatientId` / `visitNo` - 建档前为null，建档后回填
- `doctorInfo.number` - 操作员编号
- `patientInfo.hisOrgId` - 单位ID

### 响应（字段名固定）

```json
{
  "code": "200",
  "message": "成功",
  "rsp": {
    "outPatientCode": "his客户ID",
    "visitNo": "就诊卡号"
  }
}
```

---

## AI配置步骤

### 步骤1：获取his接口信息

**如客户已提供** → 直接使用
**如未提供** → 询问：
1. his建档接口的入参字段？
2. his响应中客户ID和就诊号的字段名？
3. 是否有固定值字段（交易码、认证串等）？
4. 接口协议是JSON还是XML？

### 步骤2：生成前置映射（Jarvis → his）

**直接映射**：
- `patientName` → `name`（NAME）
- `identitycard` → `cardId`（NAME）

**嵌套提取**：
- `doctorInfo` → `operatorId`（EXPRESSION: `#data[number]`）

**值转换**：
- `sex` → `sex`（EXPRESSION: `#data == '1' ? '男' : '女'`）

**固定值**：
- `type` = `"1"`（CONSTANT）

### 步骤3：生成后置映射（his → Jarvis）

**JSON响应**：
- `data.patientId` → `rsp.outPatientCode`
- `data.clinicId` → `rsp.visitNo`
- `returnCode == "1" ? "200" : "-1"` → `code`

**XML响应**：
- `Body.PatientID` → `rsp.outPatientCode`
- `Body.VisitNo` → `rsp.visitNo`
- `Head.TradeStatus == 'AA' ? '200' : '-1'` → `code`
