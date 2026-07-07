# CreateArchives（鑫达 HIS 建档）

对应旧项目 `hems-interface-xinda` 中：

```java
case METHOD_SYNC_HIS_CREATE_ARCHIVE: // "CreateArchives"
    myResponseModel = hisWebService.createHisArchive(jarvisRequest);
```

工作流定义：[`create_archives_workflow.json`](workflows/create_archives_workflow.json)

## 调用链路

1. `prepare_archive_context`：从 Jarvis `data` 归一化患者字段（身份证/手机号查档规则、生日、婚姻、团检标志等）。
2. `GetPatInfo`：`msgCode=GetPatInfo`，有身份证用 `codetype=5`，否则 `codetype=2` 且卡号为 `HL+手机号`。
3. 已建档：
   - 若 `ybdm=131`，调用 `UpPatInfo` 改为自费 `ybdm=1`。
   - 返回 `outPatientCode` / `registrationExtend1` / `registrationExtend2` 及体软回写字段。
4. 未建档：调用 `RegPatInfo`，成功后返回同样字段。

## 传输方式说明

鑫达 HIS 不是 SOAP Envelope，而是：

- URL：`his.webservice.url`（默认 `http://172.16.20.10:8099/WebInterface.asmx/SendEmr`）
- `Content-Type: application/x-www-form-urlencoded`
- 表单字段：`msgCode` + `sendXml`
- 响应：`<string xmlns="http://www.winning.com.cn/">...</string>`，需去壳并还原 `&lt;` / `&gt;`

因此本工作流使用 `HTTP` + `INLINE(XML.stringify/XML.parse)`，而不是 `WEBSERVICE.bodyTemplate`。

## 示例入参

```json
{
  "BusinessMethod": "CreateArchives",
  "BusinessCode": "2508190082",
  "hisWebserviceUrl": "http://172.16.20.10:8099/WebInterface.asmx/SendEmr",
  "data": {
    "patientName": "测试账号",
    "sex": "2",
    "birthday": "1972-01-01",
    "identityCard": "",
    "phone": "13059970500",
    "address": "杭州市",
    "marriage": "2",
    "userType": "0",
    "orgName": ""
  }
}
```

## 成功出参（对齐 MyResponseModel）

```json
{
  "code": 200,
  "message": "OK",
  "rsp": {
    "outPatientCode": "12345",
    "registrationExtend1": "HL13059970500",
    "registrationExtend2": "BLH001",
    "hemsVocaPatientHisPatientId": "12345",
    "hemsVocaPatientVisitNo": "BLH001"
  }
}
```
