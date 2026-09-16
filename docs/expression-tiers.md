# 表达式分层

iFlow 将全局数据流转与任务内部取值分开：框架解析 `${...}`，任务按自己的宿主语言解释 `$.`。

| | 编排层 | 任务层 |
|---|---|---|
| 标记 | `${...}` | `$.字段`（具体能力由任务决定） |
| 转义 | `$${` | 遵循宿主语言；JDBC 引号内的 `$.` 是原文 |
| 求值时机 | Decider 调度阶段（TaskMapper） | Task 执行阶段 |
| 根上下文 | 全局工作流上下文：`workflow.*`、`{taskRef}.*` | 该任务自己的局部参数 |
| 职责 | 把上游业务数据搬进任务的 `inputParameters` | 把局部参数绑定进任务内部的模板 |
| 实现 | `ParametersUtils` | 各任务模块，例如 JDBC 的 `SqlTemplate` |

编排层完整 `${...}` 表达式保留原始类型，嵌在文本中则按字符串替换。JDBC 任务层只接受确定的字段路径，复用 Jayway JsonPath 读取 payload；缺失叶子字段绑定 null，无效父节点或类型不匹配时报错。INLINE / MAPPING 的 `$` 是 JavaScript 变量，按各任务的脚本契约使用。

## 为什么要分两层

编排层的职责是数据流转，它不该关心任务内部长什么样。如果在 SQL 里直接写 `${workflow.input.dept}`，SQL 模板就绕过 `inputParameters` 直接依赖了全局编排上下文，任务不再自包含。

反过来，任务内部的模板需要区分「值」和「代码」。SQL 没有变量的概念，`WHERE name = ${workflow.input.name}` 会被替换成 `WHERE name = 张三`——既是语法错误也是注入口子。任务层标记的存在就是为了让引擎知道哪里是值，从而改写成宿主语言的绑定形式。

## 任务层落地情况

| 任务 | 任务层 | 局部参数根 | materialize 方式 |
|---|---|---|---|
| JDBC | `$.字段` | `payload` | 改写成 `:_pN` 命名绑定参数，数组自动展开 |
| INLINE | JS 全局变量 `$` | 任务 input | GraalJS 绑定 |
| MAPPING | `$` 和命名循环变量 | 原始 payload 与当前循环作用域 | GraalJS 绑定 |
| HTTP / WEBSERVICE | 无 | — | 载荷全是文本，编排层的 `${...}` 已足够 |

INLINE 与 MAPPING 的宿主语言是 JavaScript，`$.id` 本身就是合法的 JS 属性访问，不需要额外标记。MAPPING 的硬编码入参是 `payload`：表达式模式把求值结果赋给它，参数模式把键值对象赋给它；执行时 `$` 始终是原始 `payload`，对象、数组和标量都不额外包装。NAME 和表达式均使用 `$.id`，循环模板通过 `item.code` 等命名变量读取当前元素；嵌套对象不会切换上下文，映射输出不会写回 payload。配置方式见 [MAPPING 目标结构构造](mapping-task.md)。

## JDBC

```json
{
  "inputParameters": {
    "url": "${workflow.input.dbUrl}",
    "vendor": "MYSQL",
    "operation": "QUERY",
    "payload": {
      "barcodes": "${workflow.input.data.barcodes}"
    },
    "sqlTemplate": "SELECT * FROM v_lis_apply WHERE barcode IN $.barcodes"
  }
}
```

所有 JDBC 配置平铺在 `inputParameters`。`payload` 是业务数据入口，`sqlTemplate` 是 SQL 模板。`$` 直接代表解析后的 payload，所以使用 `$.barcode`，不额外加一层 `$.payload`。

### SQL 模板参数

`payload` 固定为 Key / Value 对象。键对应 SQL 模板中的 `$.字段`，值支持 `${...}`；完整表达式解析为数组、对象、数值或布尔值时保留类型。

```json
{
  "payload": {
    "barcodes": "${workflow.input.data.barcodes}",
    "patient": "${workflow.input.data.patient}",
    "status": "READY"
  },
  "sqlTemplate": "SELECT * FROM v_lis_apply WHERE barcode IN $.barcodes AND status = $.status"
}
```

控制台的模板参数值支持文本和 JSON；例如 `["A001","A002"]` 保存为数组，`123` 保存为数值，`"123"` 保存为字符串。编辑其他字段或改名时保留已有值的类型。payload 中的 JSON 字符串不会在任务执行时再次解析成对象。

### 数组展开

当 payload 为 `{"barcodes":["A001","A002"]}` 时，`IN $.barcodes` 自动生成 `IN (:_p0)`，再由 `NamedParameterJdbcTemplate` 展开成 `IN (?, ?)` 并分别绑定两个字符串。用户无需遍历数组、添加引号或拼接 SQL。

`IN ($.barcodes)` 沿用已有括号；`NOT IN` 规则相同；`IN $.barcode` 的标量值按单个参数绑定。空数组会在连接数据库前报错，避免生成 `IN ()`。普通 `barcode = $.barcode` 生成一个绑定参数，不增加括号。

### 规则

- 支持确定路径：`$.barcode`、`$.patient.id`、`$.items[0].code`、`$['特殊字段名']`。payload 根必须是对象，数组放在命名参数下；数组索引为非负整数，带连字符或点的字段名使用括号形式。
- 不支持通配符、递归查找、筛选和脚本计算；这些数据处理交给上游任务。路径之外的 `-`、`+`、`::` 等仍按 SQL 处理。
- SQL 字符串、引用标识符和注释里的 `$.` 保留原文。例如 `JSON_EXTRACT(doc, '$.barcode')` 中的路径由数据库处理。
- 模糊查询使用 `LIKE CONCAT('%', $.keyword, '%')`；`LIKE '%$.keyword%'` 是普通字符串，不替换。
- 绑定值可以是标量、null 或非空的一维标量数组。对象、嵌套数组和非法路径在连接前报错。缺失叶子字段绑定 null；`= null` 仍遵循 SQL 的 null 比较语义。
- payload 必须是对象；无任务路径的 SQL 使用空对象 `{}`，数组需要放在命名参数下。
- 绑定参数不能替代表名、列名、排序方向。动态 SQL 结构由上游生成，使用 `"sqlTemplate": "${build_sql_ref.output}"` 传入，并保留其中的 `$.字段` 给 JDBC 绑定。
- 本次重构直接移除 `jdbc_request`、`parameters`、`sql` 和 `#{...}` 旧契约，现有定义需改为新结构。
