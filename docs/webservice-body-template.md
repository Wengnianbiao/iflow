# WebService `soapTemplate`

WEBSERVICE 与 HTTP 共用同一套扁平 `inputParameters` 形状，唯一差异：

- HTTP：`body`
- WEBSERVICE：`soapTemplate`（SOAP/XML 字符串模板）

`soapTemplate` 由 `WebServiceTaskMapper` + `ParametersUtils` 在调度阶段替换，发送时不再二次求值。`SOAPAction` 放在 `headers` 里。

## 模板参数（推荐）

先在 `templateParameters` 中把工作流或前置任务数据映射为局部参数，再在 XML 中通过 `${$.参数路径}` 引用：

```json
{
  "uri": "${workflow.input.serviceUri}",
  "headers": {
    "SOAPAction": "GetPatient"
  },
  "templateParameters": {
    "payload": "${workflow.input}"
  },
  "soapTemplate": "<Envelope><Body><GetPatient><id>${$.payload.patient.id}</id></GetPatient></Body></Envelope>",
  "contentType": "text/xml;charset=UTF-8",
  "connectionTimeOut": 3000,
  "readTimeOut": 3000
}
```

`templateParameters` 的映射值先按原有 Conductor 表达式解析，因此也可以映射前置任务输出：

```json
{
  "templateParameters": {
    "patient": "${query_patient_ref.output}"
  },
  "soapTemplate": "<GetPatient><id>${$.patient.id}</id></GetPatient>"
}
```

局部参数名不能使用 `workflow` 或已存在的任务引用名。

## 旧语法兼容

原有直接引用继续有效，也可以和局部参数写在同一个模板中：

```xml
<Request tenant="${workflow.input.tenant}">
  <patientId>${$.payload.patient.id}</patientId>
</Request>
```

占位符保持原始文本替换语义，不会自动做 XML 转义。不要直接插入可能包含 `&`、`<`、`>` 的自由文本。

## 动态结构和数组

数组、重复标签等应由前置 INLINE 用 `XML.stringify` 生成完整 XML，再通过模板参数整值引用：

```json
{
  "uri": "${workflow.input.serviceUri}",
  "templateParameters": {
    "requestXml": "${build_xml_ref.output}"
  },
  "soapTemplate": "${$.requestXml}"
}
```

完整可运行定义见 [`workflows/webservice_body_template_workflow.json`](workflows/webservice_body_template_workflow.json)。
