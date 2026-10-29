# 工作流编辑校验

画布、配置表单、源码共用 `workflowDefinitionMachine` 中的定义和校验结果。底部错误栏由持续存活的 `workflowValidationMachine` 管理，布局参考本仓库 Conductor 的 ErrorInspector。

可选引用类型字段允许 `null`，按未配置处理，校验和无关字段编辑保留原值。iFlow 后端的任务字段 `defaultCase`、`evaluatorType`、`expression`、`loopCondition`、`loopItems` 默认就是 `null`，并非结构错误；`decisionCases` 默认 `{}`，`forkTasks`、`joinOn`、`defaultExclusiveJoinTask`、`loopOver` 默认 `[]`。任务专属必填配置仍按类型检查，例如 INLINE 使用 `inputParameters.expression`，SWITCH 使用顶层 `expression`。必填标识为空、任务列表中的 `null` 元素或已配置字段的错误类型仍会报错。

## 编辑与保存

- 源码文本由父状态机持有。有效编辑同步到表单和画布；JSON 或任务树结构错误保留原文，暂停表单编辑，画布保留最近可展示的定义。
- 工作流 `route` 与 `version` 唯一确定定义，`name` 是业务名称，允许中文和重复。表单和源码均可编辑路由；已保存定义修改路由后保存会调用创建接口，保留编辑中的版本并保留原工作流。路由和版本不变时修改名称只更新当前定义，联合主键冲突按创建规则报错。
- 引用名称必须唯一，不能使用画布的保留名称。空引用或重复引用暂停画布修改；任务表单按任务路径保持身份，修改引用不会误改另一个任务。
- HTTP body、SPI spiInput、MAPPING paramsConfig、自定义任务 JSON 和 SWITCH 分支名称的未完成草稿留在父状态机中。切换标签或任务后仍可恢复。工作流超时的未完成配置同样保留为字段草稿。存在未完成草稿时暂缓源码和其他配置范围的修改，以避免切换编辑丢失草稿。
- 保存请求检查最新的定义和所有字段草稿，有错误时展开错误栏并定位首个错误。无错误时进入只读差异确认；确认和请求期间的编辑事件被状态机拒绝，保证提交内容与确认内容一致。
- 服务端保存失败保留定义，在保存确认和底部错误栏显示原因，允许重试。重置恢复 baseline 并清除原始草稿；切换工作流时重置校验 actor，加载期间不显示旧的编辑面板。
- 删除确认和删除 API 使用 baseline 中已加载的路由和版本，编辑草稿中的路由或版本不会改变删除目标。
- 未完成源码和参数草稿计入未保存状态，刷新或关闭页面会触发现有的离开提示。普通站内导航的拦截仍需后续统一实现。

## 扩展 task 的校验

保留 iFlow 的 task 插件注册机制，不使用 Conductor task 白名单。未知 task 仍可编辑并保存；定义层只检查公共结构和引用。内置 HTTP、INLINE、SWITCH/DECISION、DO_WHILE、JDBC、WEBSERVICE、SPI、MAPPING 的静态配置规则由各 task 插件提供。

```ts
registerTaskPlugin({
  type: 'CUSTOM_HL7',
  validate: (task, workflow) => task.inputParameters?.channel
    ? []
    : [{
        code: 'CHANNEL_REQUIRED',
        path: '/inputParameters/channel', // 相对于该任务的 JSON pointer
        message: '请选择通道',
        severity: 'error', // warning 只提示，不阻止保存
      }],
})
```

插件校验为同步纯函数，可覆盖同类型内置校验；错误定位采用 JSON pointer，不按引用名称搜索源码，因此嵌套任务和重复引用能准确定位。自定义表单可以使用 `ValidationField` 标注字段，并使用 `useFieldDraft` 保存尚不能应用到定义的文本；已有任务表单继续支持 `useTaskFieldDraft`。

校验只检查编辑时能确定的问题，不执行 Java、JS、SQL 或远程请求，也不判定 `${...}` 的运行时值。后端和 task 执行器仍负责最终的运行时校验。后端元数据保存接口检查路由格式、名称非空以及版本为正整数，并递归检查节点策略的非负数值；更新接口要求目标路由和版本与请求体一致，并只更新已有定义。

公共任务表单中的“执行策略”提供启动延迟与 `optional` 配置，非内置控制流节点额外显示“失败重试”，INLINE、MAPPING 的终止错误仍不重试。不提供通用任务超时配置。四项策略分别通过开关配置：关闭时继承框架配置，开启后必须填写或选择，未完成的草稿阻止保存。缺省或 null 显示为关闭，显式 0 保留并显示为开启；数值必须为 Integer 范围内的非负整数，重试方式必须合法。保存不填入框架默认值，启动时在独立定义快照中补齐启动延迟，并仅为非内置控制流节点补齐重试默认值。参见[任务执行策略配置](task-execution-policy.md)。

验证命令：`pnpm test:definition`，以及 `node --experimental-strip-types --test tests/*.test.mjs`。

DO_WHILE 的 `loopItems` 和 `loopCondition` 严格互斥：数组来源只能是完整的 `${...}` 表达式，JS 条件固定由 GraalJS 计算；两者都缺失、都配置、选用字段为空或循环体为空均不能保存。`inputParameters.loopItems` 为数组快照保留键。运行时要求数组来源非 null 且为集合或数组。
