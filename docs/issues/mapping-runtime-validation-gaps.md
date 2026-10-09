# MAPPING 后端测试发现的现有问题与回归限制

补齐 MAPPING 后端测试时发现以下现有行为。本次只补充测试与测试资源，未调整运行时语义。

## NAME 路径允许末尾函数

`ParamSourceReader` 声明取值路径仅支持确定的字段与数组下标，但点号字段的正则允许括号；
`JsonPath.isDefinite()` 也不会排除末尾函数。

- 配置：`mappingType: NAME`、`targetParamType: INTEGER`、`sourceParamKey: $.groups.length()`。
- payload：`{"groups": [[1], [2]]}`。
- 实际行为：路径通过校验，输出 `2`。
- 契约差异：函数选择应由 EXPRESSION 表达；NAME 路径应在执行前拒绝函数。

待后续明确路径语法并修复校验，再将末尾函数加入非法路径回归用例。当前新增测试覆盖通配符、过滤、递归、切片和非法下标。

## JS 绑定数组不能通过 push 扩容

表达式中的 payload 与循环变量通过脚本代理暴露数组，现有数组代理不支持新增下标。

- payload：`{"profile": {"tags": ["root"]}}`。
- 表达式：`$.profile.tags.push('changed'); $.profile`。
- 实际行为：任务失败，原因包含 `Index 1 out of bounds for length 1`。
- 影响：脚本能够修改已有数组下标，但不能直接对绑定数组使用 `push()` 扩容。

本次隔离测试用 `$.profile.tags[0] = 'changed'` 与循环变量的嵌套数组下标赋值，
验证脚本修改不会传播到原 payload、其他字段或后续调用。数组扩容能力留待脚本代理单独处理。

## 依赖模块完整回归未通过

运行 `mvn -o -pl iflow-mapping-task -am test` 时，当前受限环境下 Mockito 动态附加 JVM 失败。
通过 Surefire 的 `argLine` 以 `-javaagent:/path/to/mockito-core-5.14.2.jar` 启动 Mockito 后，
该环境问题得到解决，但已有的 `WorkflowExecutorOpsThreadPoolTest` 仍有以下问题：

| 用例 | 观察到的结果 |
|---|---|
| `schedulesDoWhileIterationOnWorkflowCallerThread` | 运行超过 180 秒；超时线程栈停在 `WorkflowExecutorOps.driveWorkflow` / `dedupAndAddTasks`，完整回归因 fork 超时失败。 |
| `executesLinearTaskOnCallerThread` | 持久化任务断言失败，测试记录的更新任务为 null。 |
| `executesForkBranchesConcurrentlyAndJoinsThem` | JOIN 报 `branch_a_2` 不存在并抛出 `TerminateWorkflowException`。 |
| `restoresOuterExecutionScopeAfterNestedJoin` | JOIN 报 `after_nested` 不存在并抛出 `TerminateWorkflowException`。 |
| `failsWorkflowWhenForkBranchExecutorRejectsTask` | 分支线程池拒绝任务时抛出 `TerminateWorkflowException`，用例未处理该异常。 |

排除其中卡住的一条用例后，其余四条失败仍可复现；这些用例和引擎生产代码均未被本次修改。
线程池测试的夹具与运行时契约需要单独检查。测试执行参数中的排除项仅用于继续验证其他用例，
没有在项目配置或源代码中禁用这些测试。

本次 MAPPING 专项共 245 条用例全部通过，其中新增 188 条，原有 8 条业务示例也恢复通过。
临时使用 `-Dtest=*,!WorkflowExecutorOpsThreadPoolTest` 并预加载 Mockito agent 后，
common / core / mapping 的其余 664 条用例全部通过（194 / 250 / 220），没有跳过用例。
完整回归的上述五条线程池用例仍未通过，不能将隔离验证结果视为完整回归通过。
