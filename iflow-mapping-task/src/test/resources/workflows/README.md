# MAPPING 工作流测试资源

这些 JSON 来自 Git 提交 `b3a1317b` 删除文档示例前的 `docs/workflows`，
用于保留 `MappingWorkflowExamplesTest` 的业务转换回归。

连接地址统一替换为 `example.invalid` 或本地测试 JDBC 地址，账号与密码替换为
`test-only`，固定应用标识替换为 `test-app`。编排引用、映射规则与任务结构保持原有行为。

测试通过类路径读取资源，只执行参数解析及本地转换，不请求这些示例中的外部服务。
