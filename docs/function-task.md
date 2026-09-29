# FUNCTION 字符串转换任务

`FUNCTION` 是同步系统任务，通过受控的 Java 内置函数做 JSON / XML 的解析与序列化。返回值直接作为任务 `output`，不添加 `result` 包装。

Open API 入口默认按 Content-Type 解析 JSON/XML，也可通过 `X-iFlow-Body-Mode: raw` 把文本交给流程自行处理；业务字段通过 `${workflow.input.body}` 读取。下例中的 `request` 是独立 FUNCTION 示例自行声明的字符串参数，适用于嵌套报文或流程中途的字符串转换，不是 Open API 的输入包装。入站契约、表单文件和迁移步骤见 [open-api-input.md](open-api-input.md)。

- `JSON_PARSE`（控制台显示为 `JSON.parse`）：把 JSON 字符串解析为对象、数组、字符串、数字、布尔值或 `null`。
- `JSON_STRINGIFY`（控制台显示为 `JSON.stringify`）：把对象、数组或标量写成紧凑 JSON 字符串；对象中的 `null` 字段会保留。显式传入 `null` 时输出字符串 `"null"`。
- `XML_PARSE`（控制台显示为 `XML.parse`）：返回对象，保留根元素和限定名；属性使用 `@` 前缀，混合文本使用 `#text`，重复同名元素转为数组。
- `XML_STRINGIFY`（控制台显示为 `XML.stringify`）：按与 `XML.parse` 相同的约定把对象写成 XML 字符串。可额外提供 `rootName` 作为根元素；省略时 `value` 必须是带单一根节点的对象，以便与 `XML.parse` 的结果互转。

解析示例：

```json
{
  "name": "parse_request",
  "taskReferenceName": "parse_request_ref",
  "type": "FUNCTION",
  "inputParameters": {
    "function": "JSON_PARSE",
    "value": "${workflow.input.request}"
  }
}
```

当 `request` 是字符串 `{"patientCode":"82601230009"}` 时，任务输出直接为对象 `{"patientCode":"82601230009"}`。

序列化示例：

```json
{
  "name": "build_json",
  "taskReferenceName": "build_json_ref",
  "type": "FUNCTION",
  "inputParameters": {
    "function": "JSON_STRINGIFY",
    "value": "${mapping_ref.output}"
  }
}
```

```json
{
  "name": "build_xml",
  "taskReferenceName": "build_xml_ref",
  "type": "FUNCTION",
  "inputParameters": {
    "function": "XML_STRINGIFY",
    "rootName": "his",
    "value": "${mapping_ref.output}"
  }
}
```

下游通过 `${parse_request_ref.output.patientCode}` 读取解析字段，通过 `${build_json_ref.output}` 读取完整 JSON 字符串。XML 解析结果例如 `${parse_xml_ref.output.patient.code}`；序列化结果是字符串，适合作为 HTTP / WebService 的请求体。

以下 `value` 使用任务配置 JSON 的写法（`JSON_PARSE`）：

| `value` | 任务 output |
|---|---|
| `"[1, 2]"` | `[1, 2]` |
| `"42"` | `42` |
| `"false"` | `false` |
| `"null"` | `null` |
| `null` | `null` |

`JSON_STRINGIFY` 是其逆操作：对象 `{ "patientCode": "82601230009" }` 输出 `{"patientCode":"82601230009"}`，`null` 输出 `"null"`。字符串输入会按 JSON 字符串规则加引号，因此序列化对象时应使用 `${task.output}` 这类解析后的值，而不是再包一层文本。

JSON 字符串本身也可作为解析结果。数组元素通过 `${parse_request_ref.output[0]}` 读取。业务返回的 `result`、`error` 都是普通字段，引擎不会添加或移除它们。

`value` 必须存在。`JSON_PARSE` / `XML_PARSE` 显式传入 `null` 时均成功并输出 `null`；非空值必须是 String。`JSON_STRINGIFY` / `XML_STRINGIFY` 接受对象、数组、标量和 `null`。缺失参数、解析函数收到非字符串值、空白字符串和非法内容都会使任务失败。`XML_STRINGIFY` 的 `rootName` 若存在则必须是字符串。失败信息记录在任务的 `reasonForIncompletion`，output 为 null。

单次输入（解析）或输出（序列化）长度默认最多 10,000,000 个字符，用来盖住不分页的目录类响应（大约一页 2 万条、单条几百字符）。这不是任意大报文的容量。可通过 `iflow.function.max-input-chars` 调整为正整数。XML 解析已禁用 DTD、外部实体、外部 DTD 和外部 Schema。

可导入定义见 [function_parse_workflow.json](workflows/function_parse_workflow.json)，完整输出约定及迁移说明见 [task-output.md](task-output.md)。
