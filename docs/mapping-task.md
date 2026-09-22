# MAPPING 目标结构构造

MAPPING 以 `inputParameters.payload` 为输入，用 `paramsConfig` 构造目标 JSON 对象，字段直接写入任务 `output`。

`$` 始终是原始 payload。对象、数组、字符串、数字和 null 均不额外包装。配置列表不能缺失或为空；如需空对象，可配置一个空的 OBJECT 字段。

## 节点行为

每个节点指定 `mappingType`、`targetParamType`，可填写 `paramDesc`。根字段和对象子字段必须填写不重复的 `targetParamKey`；数组元素模板不填写字段名。

| mappingType | 配置 | 行为 |
|---|---|---|
| NAME | sourceParamKey | 从 payload 或循环变量读取标量 |
| CONSTANT | mappingRule | 原生 JSON 标量常量，包括字符串、数字、布尔和 null |
| EXPRESSION | mappingRule | 非空 JS 字符串，直接使用结果 |
| OBJECT | childParams | 递归构造对象，targetParamType 为 OBJECT |
| ARRAY_MAP | sourceParamKey、itemAlias、itemTemplate | 遍历源数组，targetParamType 为 ARRAY |
| ARRAY_FIXED | items | 按模板顺序生成固定长度数组，targetParamType 为 ARRAY |

目标类型支持 STRING、INTEGER、LONG、DOUBLE、BOOLEAN、OBJECT、ARRAY。对象只允许 OBJECT 和 EXPRESSION，分别对应对象构建和 JS 表达式；数组只允许 ARRAY_MAP、ARRAY_FIXED 和 EXPRESSION，分别对应循环映射、固定长度和 JS 表达式。各取值模式都会检查非空结果的目标类型；标量沿用现有类型转换，不隐式解包、取首项或包装。

NAME 路径必须以 `$` 或可用的循环变量开头，例如 `$.patient.id`、`$[0]`、`item.code`、`$['field.name']`。仅支持确定的字段与下标路径，过滤等复杂选择使用 EXPRESSION 或前置 INLINE。

## 对象生成方式

需要重新组织对象字段时使用 OBJECT，通过 `childParams` 逐字段构建。需要整体计算或直接返回对象时使用 EXPRESSION，例如将 payload 中的 `patient` 作为独立对象写入输出：

```json
{
  "targetParamKey": "patient",
  "targetParamType": "OBJECT",
  "mappingType": "EXPRESSION",
  "mappingRule": "$.patient"
}
```

对象不支持 NAME 或 CONSTANT。EXPRESSION 的非空结果必须是对象，否则任务失败并报出目标路径；缺失值仍保留为 `null`。

## 数组生成方式

- 循环映射：使用 ARRAY_MAP 遍历源数组并按元素模板重塑。
- 固定长度：使用 ARRAY_FIXED 逐项配置；`items` 数量就是输出数组长度。
- JS 表达式：使用 EXPRESSION 整体计算或直接返回数组，例如 `$.rows`。

控制台会根据 `ARRAY_MAP` 唯一 `itemTemplate` 的目标类型，将其显示为“对象元素”“标量元素”或“数组元素”节点。对象元素可继续添加子字段，数组元素可继续配置嵌套数组结构。

数组不支持 NAME 或 CONSTANT；嵌套数组和数组元素模板遵守相同规则。

## 循环数组

```json
{
  "payload": {
    "patientId": "P1",
    "items": [{ "code": "A", "qty": 2 }, { "code": "B", "qty": 1 }]
  },
  "paramsConfig": [{
    "targetParamKey": "details",
    "targetParamType": "ARRAY",
    "mappingType": "ARRAY_MAP",
    "sourceParamKey": "$.items",
    "itemAlias": "item",
    "itemTemplate": {
      "targetParamType": "OBJECT",
      "mappingType": "OBJECT",
      "childParams": [
        { "targetParamKey": "itemCode", "targetParamType": "STRING", "mappingType": "NAME", "sourceParamKey": "item.code" },
        { "targetParamKey": "quantity", "targetParamType": "INTEGER", "mappingType": "NAME", "sourceParamKey": "item.qty" },
        { "targetParamKey": "patientId", "targetParamType": "STRING", "mappingType": "NAME", "sourceParamKey": "$.patientId" },
        { "targetParamKey": "enabled", "targetParamType": "BOOLEAN", "mappingType": "CONSTANT", "mappingRule": true }
      ]
    }
  }]
}
```

输出：

```json
{"details":[{"itemCode":"A","quantity":2,"patientId":"P1","enabled":true},{"itemCode":"B","quantity":1,"patientId":"P1","enabled":true}]}
```

即使对象元素只有一个字段，也仍生成对象。要生成 `["A", "B"]`，将 `itemTemplate` 改为：

```json
{ "targetParamType": "STRING", "mappingType": "NAME", "sourceParamKey": "item.code" }
```

循环变量只在该元素模板及其子节点有效。嵌套循环可给外层命名 `order`、内层命名 `line`，循环源写 `order.lines`，内层表达式可同时读 `$.patientId`、`order.id` 和 `line.code`。变量不能重名，也不能使用 `$`、`XML`、`Crypto`、JS 关键字或内置全局变量名。

## 固定长度数组

`items` 中每个模板恰好生成一个元素；单对象包装用一个对象模板表达。下面生成 `{"patients":[{"id":"P1"}]}`：

```json
{
  "targetParamKey": "patients",
  "targetParamType": "ARRAY",
  "mappingType": "ARRAY_FIXED",
  "items": [{
    "targetParamType": "OBJECT",
    "mappingType": "OBJECT",
    "childParams": [{ "targetParamKey": "id", "targetParamType": "STRING", "mappingType": "NAME", "sourceParamKey": "$.patientId" }]
  }]
}
```

元素可以是标量、对象、数组或 null。`items: []` 生成空数组。完整可导入示例见 [mapping_arrays_workflow.json](workflows/mapping_arrays_workflow.json)。

## 空值、顺序与错误

- 普通取值缺失保留 null，不省略目标字段。
- 循环源缺失、null 或空数组生成 `[]`，非数组值报错。
- 执行前验证整棵配置树，因此空源数组不会隐藏无效模板或作用域错误。
- 对象不改变上下文，输出也不会写回输入。交换独立字段顺序不改变取值；表达式对 payload 或循环变量的修改不会传播到其他字段。
- 错误包含目标路径；元素执行错误包含实际下标，例如 `MAPPING $.details[1].quantity`。诊断写入任务失败原因。

## 从旧模型迁移

本模型不兼容 `sourceParamType`、裸字段路径及根据子字段数量推断元素形状的旧规则。删除源类型，显式选择节点行为，并将根路径改为 `$.field`。旧的对象上下文切换改为子字段上的完整路径。

不得读取同级已映射输出。`CreateArchives` 示例已将 `JSON.stringify([$.patient])` 移到后续 INLINE，通过其 input 引用 MAPPING 产出的 patient，然后由 HTTP 引用 INLINE 的字符串 output。

运行中的数据库工作流不自动迁移，应在部署新版本前同步调整配置。
