# Open API 入参

`GET /hl-open-api/**` 和 `POST /hl-open-api/**` 继续由统一网关接入。去掉路径前导 `/` 后作为工作流名称，例如 `/hl-open-api/query-lis-apply` 路由到 `hl-open-api/query-lis-apply`。响应规则保持原行为。

## JSON 与 XML

Postman 选择 POST → Body → raw → JSON，直接发送业务报文：

```json
{"barCode":"00123"}
```

也可以直接发送数组。调用方不需要增加 `body` 或 `request` 包装：

```json
[{"barCode":"00123"},{"barCode":"00456"}]
```

默认工作流输入如下。`files` 始终存在，没有上传文件时为空对象：

```json
{
  "body": {"barCode":"00123"},
  "query": {},
  "headers": {"content-type":"application/json"},
  "files": {},
  "method": "POST",
  "path": "/hl-open-api/query-lis-apply"
}
```

| 表达式 | 含义 |
|---|---|
| `${workflow.input.body}` | 完整业务报文，支持对象、数组、标量和 null |
| `${workflow.input.body.barCode}` | 对象字段 |
| `${workflow.input.body[0].barCode}` | 数组第一项字段 |
| `${workflow.input.query.barCode}` | URL 查询参数 |
| `${workflow.input.headers['x-trace-id']}` | 请求头；名称统一小写 |
| `${workflow.input.files.file[0].fileRef}` | `file` 表单字段中的第一个临时文件引用 |

GET 的 `body` 为 null，参数只放在 `query`。POST 的 query 与 body 也独立保存。查询参数和请求头的单个值为字符串，重复值为字符串数组。业务报文自身带有 `request` 字段时，它保留在 `body.request`。

## 解析模式

请求头 `X-iFlow-Body-Mode` 控制文本请求体的处理方式。未提供时默认 `auto`；值忽略大小写和首尾空白。只允许一个 `auto` 或 `raw` 值，空值、未知值和重复请求头返回 HTTP 400。

| Content-Type | `auto` | `raw` |
|---|---|---|
| `application/json`、`application/*+json` | 原生 JSON 值 | 解码后的原始文本 |
| `application/xml`、`application/*+xml`、`text/xml` | XML 对应的 Map/List | 解码后的原始文本 |
| `text/plain` | 原始文本 | 原始文本 |
| `application/x-www-form-urlencoded` | 字段对象 | 原始编码文本 |
| `multipart/form-data` | 拆分文本字段和文件引用 | 与 `auto` 相同，不解析字段内容或文件内容 |

`raw` 模式仍会检查媒体类型、字节大小和字符编码，但不会校验 JSON/XML 文档结构。例如需要在工作流内自行解析 JSON 时，可以发送：

```http
X-iFlow-Body-Mode: raw
Content-Type: application/json
```

JSON 和普通文本默认使用 UTF-8，显式 charset 优先。XML 在未声明 HTTP charset 时按 BOM、字节签名或 XML 编码声明解码。`auto` 模式下，JSON/XML 格式错误返回 HTTP 400；非空报文缺少 Content-Type、类型不支持或 charset 不支持返回 HTTP 415。零字节请求体的 `body` 为 null，空白 JSON/XML 不是有效文档。

XML 保留根节点和限定名，属性使用 `@`，混合文本使用 `#text`，重复同名元素形成数组；叶子文本保持字符串。DTD、外部实体和外部 Schema 均被禁用。

## 表单

`application/x-www-form-urlencoded` 和 multipart 的文本字段都放在 `body`。字段不推断数字、布尔值、嵌套 JSON 或方括号语义；同名字段形成字符串数组，空值保留为空字符串。URL 查询参数直接从查询串解析，只进入 `query`，不会与表单字段混合。

例如 multipart 中的 `itemResults` 即使看起来像 JSON，仍是普通字符串：

```json
{
  "body": {
    "examNo": "00123",
    "itemResults": "[{\"examElementId\":\"3837\"}]"
  },
  "files": {
    "file": [
      {
        "fileName": "report.pdf",
        "contentType": "application/pdf",
        "size": 12345,
        "fileRef": "upload-..."
      }
    ]
  }
}
```

文件按表单字段名分组，每组始终是数组。文件名和 `contentType` 直接采用上传元信息；有文件名的零字节文件会保留，未选择文件产生的空占位项会忽略。

## 文件内容与生命周期

网关只解析 multipart 外层边界，以取得字段名、上传元信息和文件原始字节。它不会识别文件实际格式，也不会把 PDF、JSON 文件、Base64 文本或 data URI 转成字符串、对象或 Base64。原始字节只在本次同步执行期间保存在请求作用域的内存资源中，业务输入和执行记录只包含文件描述对象。

每次 `FileAccess.open(fileRef)` 都返回独立输入流，可重复或并发读取。自定义 Java 任务可通过运行中的 `WorkflowModel` 读取：

```java
try (InputStream input = workflow.getFileAccess().open(fileRef)) {
    // 按业务协议使用原始字节
}
```

INLINE 只有在脚本显式调用时才转换内容：

```javascript
Files.readText($.fileRef, 'UTF-8')
Files.toBase64($.fileRef)
```

同步执行成功、失败、超时或启动异常后，文件作用域都会关闭，所有 `fileRef` 立即失效。执行记录中保留的 `fileRef` 仅用于说明当次输入，之后不能再次读取。

## 原文与大小限制

```yaml
iflow:
  open-api:
    max-body-bytes: 10485760 # 10 MiB，必须为正整数
    include-raw-body: false
```

开启 `include-raw-body` 后，文本型 POST 输入增加 `rawBody`，保存解码后的原始文本，不通过重新序列化生成。multipart 不保存整个 MIME 报文的 `rawBody`，文件内容也不会进入 `rawBody`。

限制按实际读取字节计算，默认 10 MiB，覆盖整个 multipart 请求。Servlet multipart 的单文件和总请求限制使用同一配置。超限返回 HTTP 413，损坏的表单、boundary 或字符编码返回 HTTP 400；这些错误都不会启动工作流。

## 统一迁移

这是工作流输入契约的破坏性升级，不提供旧字符串模式；代码和已发布定义必须在同一发布窗口更新。

1. 暂停 Open API 流量，导出所有 `hl-open-api/` 工作流的所有版本，保存原始 JSON 作为回滚备份。通过 `GET /api/workflow/metadata/all` 获取名称，再用 `/versions?name=...` 和 `/detail?name=...&version=...` 获取完整定义；名称需 URL 编码。
2. POST 定义的 `inputParameters` 从 `request` 改为 `body`；GET 定义改为 `query`。同一流程如同时接收 GET/POST，应显式按 `workflow.input.method` 选择来源。
3. 删除只对整个入口报文执行 `JSON_PARSE` / `XML_PARSE` 的节点，下游引用替换为 `${workflow.input.body}`。更新关联表达式、输出表达式和嵌套分支中的引用。
4. 将直接引用 `${workflow.input.request}` 的任务改为结构化输入，移除 INLINE 中对入口字符串的 `JSON.parse`；保留业务字段解包、数组组装、嵌套字符串解析和下游协议序列化。确实需要自行解析入口报文的流程改用 `X-iFlow-Body-Mode: raw`。
5. 审查被删除任务引用，特别是 `joinOn`、`startTasks` 等非表达式配置，确保没有残留。
6. 使用 `PUT /api/workflow/metadata` 提交完整定义，重新 GET 验证所有版本，保持名称和版本号不变。通过元数据 API 更新以同步运行时缓存，不直接 SQL 替换 `json_data`。
7. 升级应用后用真实 JSON、XML 和表单请求验证，再恢复流量。回滚需同时恢复旧应用和备份定义。

仓库中的 `query-item`、`query-lis-apply`、`lis-status` 已按 POST 迁移；它们不是任意数组自动批处理的实现。现有 FUNCTION 仍用于独立字符串、嵌套报文以及流程中途转换。

验证命令：`mvn -pl iflow-gateway,iflow-mapping-task -am test`。
