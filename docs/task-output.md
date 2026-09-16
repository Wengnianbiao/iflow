# 任务与工作流输出

任务 `TaskModel.outputData`、运行时视图 `Task.outputData` 以及工作流 `output` 使用 `Object`。
对外数据应可序列化为 JSON：对象（Map）、数组（List）、字符串、数字、布尔值或 null。
引擎不统一添加 `result`，也不根据返回类型决定是否展开。

| 任务 | 输出 | 引用示例 |
|---|---|---|
| INLINE / FUNCTION / SPI | 原始返回值 | `${parse.output}`、`${parse.output[0].id}` |
| HTTP / WEBSERVICE | 完整响应对象 | `${http.output.body}`、`${http.output.statusCode}` |
| JDBC | 查询或更新结果对象 | `${jdbc.output.rows}`、`${jdbc.output.updateCount}` |
| MAPPING | 映射后的对象 | `${mapping.output.patientId}` |
| JOIN | 分支引用名到各分支原始输出的 Map，保留空对象、空数组和 null | `${join.output.branchA[0]}` |
| EXCLUSIVE_JOIN | 选中分支的原始输出 | `${join.output}` |
| DO_WHILE | iteration、loopItem、loopIndex 及按迭代编号聚合的对象 | `${loop.output['1'].parse[0]}` |

内置控制任务只在已知输出为 Map 时调用 `outputAsMap()` / `addOutput()`。对数组、标量或 null 调用这些方法会明确报错，不会隐式包装或丢弃原值。

执行失败由 `status` 和独立的 `reasonForIncompletion` 表示（引擎模型字段为 `reasonForInCompletion`）。`addError(...)` 只补充诊断；不会往业务 output 写入 error。第三方业务对象的 `error`、`result` 字段均原样保留。HTTP 非成功响应仍可保留完整响应供排查，不能仅凭 output 是否为 null 判断执行成功。

## 工作流出口

- 配置了非空 `outputParameters`：按键值映射构建最终对象，值可以引用任意类型的任务输出。
- 未配置输出映射：完整保留最后一个任务的输出，包括数组、标量和 null。
- TERMINATE 显式提供 `workflowOutput`：优先使用该值，空对象、空数组、null 同样有效；不再包装成 `{ "output": ... }`。
- Open API 支持直接返回字符串和其他 JSON 值；现有对象中的 `response` 特殊字段处理保持有效。
- Jarvis 成功时直接返回工作流输出，不限定对象结构；工作流失败或调度异常时，才返回 `{ "code": "-1", "message": "..." }` 错误对象。

## 配置与错误

完整占位符 `${task.output}` 保留类型；嵌入文本的占位符仍按字符串替换。任务在执行时检查自己需要的输入类型，不能在保存配置时推断所有第三方响应类型。

表达式语法错误、在不匹配的结构上取字段或索引会报告表达式路径；缺失叶子字段继续返回 null。未执行分支的任务输出引用继续返回 null。显式传入的 null 不会在参数克隆时变成缺失参数。

## 上线前迁移

- 原 INLINE 引用 `${ref.output.result}` 改为 `${ref.output}`，`${ref.output.result.name}` 改为 `${ref.output.name}`。脚本本身返回的业务字段 `result` 保留，不能无条件删除所有 `.result`。
- FUNCTION 直接返回解析值或序列化字符串，旧根对象引用继续有效；曾采用 result 包装的定义应移除引擎包装层。仓库中的示例与 UI 模板已同步。
- SPI 接口改为 `Object remoteInvoke(Map<String, Object> input)`。返回 Map 的源码实现仍可通过 Java 协变返回类型继续使用；已编译扩展需重新编译。
- 原诊断引用 `${ref.output.error}` 改为 `${ref.reasonForInCompletion}`。历史运行记录不自动改写。
- 数据库中已保存的流程、外部导出的定义和调用方 DTO 需要按上述规则更新；不提供随结果形状自动拆包的兼容逻辑。
