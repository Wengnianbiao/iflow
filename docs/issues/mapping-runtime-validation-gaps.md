# MAPPING 后端测试发现的问题与回归记录

补齐 MAPPING 后端测试时发现以下现有行为。初次补齐只修改测试与测试资源；
后续修复了阻塞完整回归的 DO_WHILE 调度、任务持久化及 JOIN 等待问题，结果见下文。

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

## 依赖模块完整回归已恢复

运行 `mvn -o -pl iflow-mapping-task -am test` 时，当前受限环境下 Mockito 动态附加 JVM 失败。
通过 Surefire 的 `argLine` 以 `-javaagent:/path/to/mockito-core-5.14.2.jar` 启动 Mockito 后，
该环境问题得到解决。此前 `WorkflowExecutorOpsThreadPoolTest` 的五条异常用例现已全部通过：

| 用例 | 原因与修复 |
|---|---|
| `schedulesDoWhileIterationOnWorkflowCallerThread` | Decider 未调用迭代创建逻辑，执行器又把已有 DO_WHILE 从派发列表中去重掉。接通循环体创建，只对任务登记去重，已有控制任务继续派发。 |
| `executesLinearTaskOnCallerThread` | 最终完成分支遗漏任务更新。现在持久化每批执行结果，并在工作流完成前写入最终 `executed` 状态。 |
| `executesForkBranchesConcurrentlyAndJoinsThem` | JOIN 将尚未调度的 `branch_a_2` 误报为不存在。现在等待当前 FORK 中仍可由待处理任务推进调度的分支尾任务。 |
| `restoresOuterExecutionScopeAfterNestedJoin` | `after_nested` 同样尚未调度。JOIN 等待判断限制在对应 FORK 的执行域及迭代，不跨域等待。 |
| `failsWorkflowWhenForkBranchExecutorRejectsTask` | 旧断言期待直接返回失败工作流，与当前异常传播契约不一致。测试改为断言抛出 `TerminateWorkflowException`，同时检查已持久化的 FAILED 工作流及失败任务；公共接口保持不变。 |

新增 13 条回归用例：

- 7 条执行器用例覆盖多轮顺序循环、列表对象/标量/null、空列表、未完成循环体、循环位于 FORK 分支、循环体内 FORK/JOIN 以及迭代失败中止。任务创建与更新使用独立快照，验证只创建一次且最终状态确实持久化。
- 6 条 JOIN 用例覆盖尚未调度的分支尾任务（普通及循环迭代）、错误的后续任务引用、不同执行域、不同迭代及未选中的 SWITCH 分支。不存在或已无法调度的引用仍然失败，避免转成无限等待。
- 执行器回归用例限制 Decider 轮数；调度退化时快速失败，不再让 Surefire 陷入空转。

2026-10-09 完整运行 `mvn -o -pl iflow-mapping-task -am test`，并通过 `argLine` 预加载 Mockito agent：

| 模块 | 通过用例数 |
|---|---:|
| iflow-common | 194 |
| iflow-core | 268 |
| iflow-mapping-task | 220 |
| 合计 | 682 |

结果为 BUILD SUCCESS，零失败、零错误、零跳过，没有排除任何用例。
此前 MAPPING 专项的 245 条用例（包含新增 188 条及恢复的 8 条业务示例）保持通过。
