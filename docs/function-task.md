# FUNCTION 字符串反序列化任务

`FUNCTION` 是同步系统任务，通过受控的 Java 内置函数把字符串反序列化为
`Map<String, Object>`。当前支持：

- `JSON_PARSE`（控制台显示为 `JSON.parse`）：JSON 顶层必须是对象。
- `XML_PARSE`（控制台显示为 `XML.parse`）：保留根元素和限定名，属性使用 `@`
  前缀，混合文本使用 `#text`，重复同名元素转为数组。

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

工作流输入：

```json
{
  "request": "{\n\t\"patientCode\": \"82601230009\",\n\t\"barCode\": \"B202601230001\",\n\t\"applyStatus\": \"3\"\n}"
}
```

结果直接写入任务输出根节点，因此下游通过
`${parse_request_ref.output.patientCode}` 取值，不经过 `result` 包装。

`value` 必须存在。显式传入 `null` 时任务成功并输出空 Map；非空值必须是
String，空字符串、非法内容以及 JSON 数组/标量根节点都会使任务失败。单次输入长度
默认最多 5,000,000 个字符，可通过 `iflow.function.max-input-chars` 调整为正整数。
XML 解析已禁用 DTD、外部实体、外部 DTD 和外部 Schema。

可导入的完整定义见 [function_parse_workflow.json](workflows/function_parse_workflow.json)。
