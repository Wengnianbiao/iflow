# 档案创建

## 档案创建 -> 业务要点

**上下游关系**：上游为**体检系统**，下游为**三方系统**（HIS 等）。体检系统将患者档案信息同步至三方；三方建档成功后返回档案挂号标识。

**节点关键字**：`keywordField` 配置为 `patientCode`（体检编号），用于调用日志检索与问题排查。

**三方典型回参**（后置映射需回填至体检系统）：

| 含义 | 说明 | 体检系统常见字段 |
|------|------|------------------|
| 档案 ID | 一个客户唯一标识，终身不变 | `rsp.outPatientCode` / `hisPatientId` |
| 门诊号 | 对应一次体检记录，每次体检可能不同 | `rsp.visitNo` / `visitNo` |

**常见值转换**（前置映射，以三方接口文档为准）：

| 字段 | 体检系统值 | 三方可能要求 | 转换示例 |
|------|-----------|-------------|----------|
| 性别 | `"1"` / `"2"` | `"男"` / `"女"` | `#data == '1' ? '男' : '女'` |
| 婚姻 | `"1"` / `"2"` | `"未婚"` / `"已婚"` | `#data == '1' ? '未婚' : '已婚'` |
| 证件类型 | `"1"` | `"0"` | `#data == "1" ? "0" : "1"` |

**嵌套字段**（`doctorInfo`、`patientInfo` 等）用 EXPRESSION 提取，如 `#data[number]`、`#data[hisOrgId]`。

---

## 档案创建 -> 上游入参（体检系统）

```json
{
  "sex": "2",
  "age": 53,
  "marriage": "2",
  "identitycard": "330511197202254629",
  "cardType": "1",
  "phone": "13059970500",
  "birthday": "1972-02-25",
  "address": "湖州",
  "patientName": "测试账号",
  "patientCode": "2509050001",
  "nation": "01",
  "nationName": "汉族",
  "hisPatientId": null,
  "visitNo": null,
  "archivesCode": "10011081",
  "archivalNo": "10011081",
  "userType": 1,
  "orgName": "测试单位",
  "patientInfo": {
    "sex": "2",
    "age": 53,
    "marriage": "2",
    "identitycard": "330511197202254629",
    "cardType": "1",
    "phone": "13059970500",
    "birthday": "1972-02-25",
    "hisOrgId": "85330",
    "address": "湖州",
    "patientName": "莫伟文",
    "patientCode": "2508190082",
    "hisPatientId": null,
    "visitNo": null,
    "userType": 1,
    "orgName": "湖州市机关单位",
    "registertime": "2025-08-19 08:28:01",
    "physicalTypes": "physical:types:A",
    "physicalTypeName": "健康体检"
  },
  "doctorInfo": {
    "idUser": 705,
    "userName": "陈元梅",
    "number": "cym0555",
    "loginName": null
  }
}
```

建档前 `hisPatientId`、`visitNo` 通常为 `null`，由后置映射从三方响应回填。

---

## 档案创建 -> 上游出参（体检系统）

```json
{
  "code": "200",
  "message": "成功",
  "rsp": {
    "outPatientCode": "三方档案ID",
    "visitNo": "门诊号"
  }
}
```

| 字段 | 说明 |
|------|------|
| `code` | 状态码，`200` 表示成功 |
| `message` | 响应消息 |
| `rsp.outPatientCode` | 三方档案 ID（一客一号） |
| `rsp.visitNo` | 门诊号（本次体检记录） |

---

## 档案创建 -> 映射配置

> 以下三方出入参仅为示例，实际字段以提供的三方接口文档为准。

### 三方入参示例

```json
{
  "name": "测试账号",
  "sex": "女",
  "idCard": "330511197202254629",
  "phone": "13059970500",
  "birthday": "1972-02-25",
  "marriage": "已婚",
  "address": "湖州",
  "operatorId": "cym0555",
  "operatorName": "陈元梅",
  "orgId": "85330",
  "type": "1"
}
```

### 三方响应示例

```json
{
  "returnCode": "1",
  "errorMessage": "",
  "data": {
    "patientId": "P20250905001",
    "clinicId": "V202509050001",
    "name": "测试账号",
    "idCard": "330511197202254629"
  }
}
```

### 参数映射

```json
[
  {
    "processType": "PRE_PROCESS",
    "sourceParamKey": "patientName",
    "sourceParamType": "STRING",
    "targetParamKey": "name",
    "targetParamType": "STRING",
    "mappingType": "NAME",
    "sort": 1
  },
  {
    "processType": "PRE_PROCESS",
    "sourceParamKey": "identitycard",
    "sourceParamType": "STRING",
    "targetParamKey": "idCard",
    "targetParamType": "STRING",
    "mappingType": "NAME",
    "sort": 2
  },
  {
    "processType": "PRE_PROCESS",
    "sourceParamKey": "phone",
    "sourceParamType": "STRING",
    "targetParamKey": "phone",
    "targetParamType": "STRING",
    "mappingType": "NAME",
    "sort": 3
  },
  {
    "processType": "PRE_PROCESS",
    "sourceParamKey": "birthday",
    "sourceParamType": "STRING",
    "targetParamKey": "birthday",
    "targetParamType": "STRING",
    "mappingType": "NAME",
    "sort": 4
  },
  {
    "processType": "PRE_PROCESS",
    "sourceParamKey": "address",
    "sourceParamType": "STRING",
    "targetParamKey": "address",
    "targetParamType": "STRING",
    "mappingType": "NAME",
    "sort": 5
  },
  {
    "processType": "PRE_PROCESS",
    "sourceParamKey": "sex",
    "sourceParamType": "STRING",
    "targetParamKey": "sex",
    "targetParamType": "STRING",
    "mappingType": "EXPRESSION",
    "mappingRule": "#data == '1' ? '男' : '女'",
    "sort": 6
  },
  {
    "processType": "PRE_PROCESS",
    "sourceParamKey": "marriage",
    "sourceParamType": "STRING",
    "targetParamKey": "marriage",
    "targetParamType": "STRING",
    "mappingType": "EXPRESSION",
    "mappingRule": "#data == '1' ? '未婚' : '已婚'",
    "sort": 7
  },
  {
    "processType": "PRE_PROCESS",
    "sourceParamKey": "doctorInfo",
    "sourceParamType": "OBJECT",
    "targetParamKey": "operatorId",
    "targetParamType": "STRING",
    "mappingType": "EXPRESSION",
    "mappingRule": "#data['number']",
    "sort": 8
  },
  {
    "processType": "PRE_PROCESS",
    "sourceParamKey": "doctorInfo",
    "sourceParamType": "OBJECT",
    "targetParamKey": "operatorName",
    "targetParamType": "STRING",
    "mappingType": "EXPRESSION",
    "mappingRule": "#data['userName']",
    "sort": 9
  },
  {
    "processType": "PRE_PROCESS",
    "sourceParamKey": "patientInfo",
    "sourceParamType": "OBJECT",
    "targetParamKey": "orgId",
    "targetParamType": "STRING",
    "mappingType": "EXPRESSION",
    "mappingRule": "#data['hisOrgId']",
    "sort": 10
  },
  {
    "processType": "PRE_PROCESS",
    "sourceParamType": "NONE",
    "targetParamKey": "type",
    "targetParamType": "STRING",
    "mappingType": "CONSTANT",
    "mappingRule": "1",
    "sort": 11
  },
  {
    "processType": "POST_PROCESS",
    "sourceParamKey": "returnCode",
    "sourceParamType": "STRING",
    "targetParamKey": "code",
    "targetParamType": "STRING",
    "mappingType": "EXPRESSION",
    "mappingSource": "RESPONSE",
    "mappingRule": "#data == '1' ? '200' : '-1'",
    "sort": 12
  },
  {
    "processType": "POST_PROCESS",
    "sourceParamKey": "errorMessage",
    "sourceParamType": "STRING",
    "targetParamKey": "message",
    "targetParamType": "STRING",
    "mappingType": "NAME",
    "mappingSource": "RESPONSE",
    "sort": 13
  },
  {
    "processType": "POST_PROCESS",
    "sourceParamKey": "data",
    "sourceParamType": "OBJECT",
    "targetParamKey": "rsp",
    "targetParamType": "OBJECT",
    "mappingType": "NAME",
    "mappingSource": "RESPONSE",
    "sort": 14,
    "childParam": [
      {
        "processType": "POST_PROCESS",
        "sourceParamKey": "patientId",
        "sourceParamType": "STRING",
        "targetParamKey": "outPatientCode",
        "targetParamType": "STRING",
        "mappingType": "NAME",
        "mappingSource": "RESPONSE",
        "sort": 1
      },
      {
        "processType": "POST_PROCESS",
        "sourceParamKey": "clinicId",
        "sourceParamType": "STRING",
        "targetParamKey": "visitNo",
        "targetParamType": "STRING",
        "mappingType": "NAME",
        "mappingSource": "RESPONSE",
        "sort": 2
      }
    ]
  }
]
```
