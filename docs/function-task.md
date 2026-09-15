# FUNCTION 字符串反序列化任务

`FUNCTION` 是同步系统任务，通过受控的 Java 内置函数解析字符串，返回值直接作为任务 `output`，不添加 `result` 包装。

- `JSON_PARSE`（控制台显示为 `JSON.parse`）：支持对象、数组、字符串、数字、布尔值和 `null`。
- `XML_PARSE`（控制台显示为 `XML.parse`）：返回对象，保留根元素和限定名；属性使用 `@` 前缀，混合文本使用 `#text`，重复同名元素转为数组。

任务配置示例：

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

下游通过 `${parse_request_ref.output.patientCode}` 读取字段，通过 `${parse_request_ref.output}` 读取完整返回值。XML 结果同样直接引用，例如 `${parse_xml_ref.output.patient.code}`。

以下 `value` 使用任务配置 JSON 的写法：

| `value` | 任务 output |
|---|---|
| `"[1, 2]"` | `[1, 2]` |
| `"42"` | `42` |
| `"false"` | `false` |
| `"null"` | `null` |
| `null` | `null` |

JSON 字符串本身也可作为解析结果。数组元素通过 `${parse_request_ref.output[0]}` 读取。业务返回的 `result`、`error` 都是普通字段，引擎不会添加或移除它们。

`value` 必须存在。两种函数显式传入 `null` 时均成功并输出 `null`；非空值必须是 String，缺失参数、非字符串值、空白字符串和非法内容都会使任务失败。失败信息记录在任务的 `reasonForIncompletion`，output 为 null。

单次输入长度默认最多 5,000,000 个字符，可通过 `iflow.function.max-input-chars` 调整为正整数。XML 解析已禁用 DTD、外部实体、外部 DTD 和外部 Schema。

可导入定义见 [function_parse_workflow.json](workflows/function_parse_workflow.json)，完整输出约定及迁移说明见 [task-output.md](task-output.md)。
