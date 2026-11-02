# iFlow 智能体指南

本指南适用于在此仓库中工作的 AI 编程智能体。

## 实现原则

- **沿用现有职责归属。** 扩展已经负责该行为的方法或类。优先使用局部辅助方法，再考虑新增类、接口、服务或执行层。仅仅因为两个调用方需要共享一项检查，并不足以引入新的抽象。
- **实现当前需求。** 新增扩展点、依赖、配置开关、兼容性重载和回退路径，必须有明确需求，或已有受支持的调用方、数据格式作为依据。仅为未来的灵活性不足以支持这些改动。
- **有针对性地遵循参考实现。** 对于需要与 Conductor 对齐的改动，编辑前先查看本地 Conductor 的对应实现和 iFlow 调用方。保留其职责边界与算法，并应用 iFlow 文档中明确的差异，不要引入无关的上游基础设施。
- **保持契约简单。** 对于职责为校验、修改状态、记录日志或抛出异常的操作，使用 `void`。调用方需要有实际意义的结果时才返回值；不要仅为了在层间传递控制而引入通用的 `updated` 标志或结果对象。
- **让控制流程清晰可见。** 在编排和持久化路径中，优先使用直接调用、显式条件和具名中间结果。不要为一组具体操作引入回调链路或通用框架。
- **遵循周边代码风格。** 命名、格式、构造方法、依赖注入、日志和异常处理均遵循附近代码的风格。不要顺带转换无关代码的风格。
- **数据模型使用普通 Java。** 实体、运行时模型、DTO 和配置类使用显式构造方法及访问器，不使用 Lombok。默认不添加构建器；仅当具体构造流程确实受益于按字段名设置值时使用，并保留现有受支持的 API。
- **测试生产契约。** 不要仅为了简化测试而新增生产接口、注入依赖或替代执行路径。优先利用现有测试切入点和可观察行为；新增抽象也必须符合生产设计。
- **保持模块依赖方向。** Core 不得依赖 starter/demo 或协议实现。行为和配置放在其所属模块中，由 starter 负责组装这些模块。

## 项目概览

iFlow 是基于 Spring Boot 的可插拔工作流引擎，设计参考 Conductor，并提供用于可视化编排的 React 控制台。项目通过 Spring Boot Starter 集成，同时提供可选的本地演示应用，用于调试和演示。

技术栈：Java 21、Maven 多模块、Spring Boot 3.4.x、React + Vite + TypeScript（`iflow-ui`）。

## 智能体文档结构

| 文件 | 适用范围 |
|------|--------|
| `AGENTS.md`（本文件） | 实现原则、仓库结构、通用检查 |
| `iflow-ui/AGENTS.md` | 前端及 Conductor UI 对齐 |
| `iflow-core/AGENTS.md` | 工作流引擎核心 |

修改模块前，先阅读该模块就近的 `AGENTS.md`；不要假设从仓库根目录开始工作时，嵌套文件已自动加载。领域专属规则应保留在对应模块文档中，不要在此重复。用户当前的指令优先于仓库指南。

## 模块结构

```
iflow-common              # 公共工具、枚举、异常、元数据 DTO
iflow-repository          # 持久化 / DAO / 领域数据存储
iflow-core                # Executor、Decider、同步系统任务抽象、锁
iflow-*-task              # 可插拔系统任务（HTTP、JDBC、WebService、ParamMap、SPI）
iflow-gateway             # 入站协议（Open API / Jarvis / HL7）
iflow-console             # 管理 / 控制台 API
iflow-ai                  # AI / MCP 辅助功能
iflow-spring-boot-starter # 自动配置与属性绑定
iflow-demo                # 本地调试 / 演示应用；通过 Maven -Pdemo 启用
iflow-ui                  # React 控制台（Vite）；不属于 Maven 模块
conductor-3.30.2/         # 上游 Conductor 参考实现（UI 与引擎模式）
```

依赖关系概览：`common` → `repository` / `core` → 任务模块及服务 → `starter`。可选的 `iflow-demo` 负责组装运行时模块，不包含在默认 Maven reactor 中。

## 修改流程

1. 查看目标代码、调用方、适用的模块指南及相关测试。对于对齐工作，还需查看对应的 Conductor 源码。按需阅读与本次行为变更相关的文档，不必通读仓库中的所有文档。
2. 涉及多项职责的改动，在编辑前简要说明预期行为及负责这些行为的现有组件。按连贯步骤推进，不要附带无关清理、新的产品语义或推测性的基础设施。
3. 将用户纠正应用于整个受影响路径，包括实现、调用方、测试和文档。删除已被取代的方案，不要再包一层或同时保留两条路径。
4. 按下述规则审查最终差异，并运行适合受影响行为的检查。报告结果、相关验证及仍存在的限制。测试通过并不能证明职责边界或项目风格得到了遵守。

## 代码审查规则

- **范围与抽象：** 每个新增类、接口、依赖、配置项和公共 API 都必须服务于所需行为。现有职责承载方能够清晰表达改动时，优先复用；删除废弃方案遗留的抽象。
- **每项规则有唯一的职责归属：** 确保配置解析、校验、策略决策和状态转换各自有明确的负责组件。其他调用方应复用这些行为，不要维护不同变体。Core 的具体职责划分见 `iflow-core/AGENTS.md`。
- **兼容性须有依据：** 保留现有公共契约和受支持的持久化数据。不要为假想调用方引入静默的空值或默认值处理、别名或兼容分支；应在适当边界显式处理无效数据。
- **行为与验证：** 同时审查所需路径及相关失败、边界场景。测试应断言可观察的契约，避免仅复述实现或为不必要的层次提供理由。

## 常用命令

```bash
# 后端
mvn clean package -Dmaven.test.skip=true
mvn -pl iflow-core -am test
mvn -Pdemo -pl iflow-demo -am clean package -Dmaven.test.skip=true
java -jar iflow-demo/target/iflow.jar

# 前端（iflow-ui）
cd iflow-ui && pnpm install
pnpm dev          # http://127.0.0.1:5174（API 代理 → IFLOW_API_TARGET 或 :8099）
pnpm exec tsc --noEmit
pnpm test:definition   # 工作流定义 / 校验变更
pnpm test:executions   # 执行搜索 / 任务索引变更
pnpm build
```

数据库初始化脚本位于 `docs/init_mysql/`，产品文档位于 `docs/`。
演示应用的源码、配置和 POM 均纳入版本控制。在 IDEA 中启用 Maven 的 `demo` profile 并重新加载项目，即可运行或调试其主类；这与 Spring profile 相互独立。Docker 脚本也是显式启用演示应用构建的入口。
后端行为变更需运行相关模块测试。UI 变更还需遵循 `iflow-ui/AGENTS.md` 中的浏览器验证和检查要求。仅修改文档时，检查差异和链接即可，无需完整构建应用。

## 通用约束

- 工作区存在未提交改动时，不要删除用户已有的修改；差异应限定在本次需求范围内。
- 优先遵循现有模块边界，不要另创新层次。
- 行为需要与 Netflix Conductor 对齐时，以 `conductor-3.30.2/` 为参考（引擎代码位于其 Java 模块中，UI 位于 `conductor-3.30.2/ui-next`）。
- 现有 UI 为中文时，面向用户的产品文案继续使用中文；技术任务类型标签（`INLINE`、`HTTP`、`SWITCH` 等）保留英文。
- 除非用户明确要求，否则不要提交密钥、本地缓存目录（`.pnpm-store`、`.m2`）或构建产物。
- 创建 Git 提交或 PR 前，先征求用户同意。

## 指南维护

- 规则应范围明确、可执行且长期有效：写明必须保持的约束和优先采用的实现路径。详细行为文档或规范源码示例应通过链接引用，不要复制到每份指南中。
- 用户要求更新，或所需行为变更导致规则过时时，再更新指南。不要仅为违反规则的实现开脱而修改指南。归纳反复出现的经验，避免将每次偶发修复都追加为新的全局禁令。

## 进一步查阅

- UI / 画布 / 定义编辑器 → `iflow-ui/AGENTS.md`
- Executor、Decider、系统任务、分叉执行 → `iflow-core/AGENTS.md`
- 同步执行行为 → `docs/synchronous-workflow-execution.md`
- 工作流超时与重启清理 → `docs/workflow-timeout.md`
- 运行时参数解析 → `iflow-core/src/main/java/com/helianhealth/iflow/core/utils/ParametersUtils.java`
