# Spring Boot Starter 接入

iFlow 通过 `iflow-spring-boot-starter` 嵌入宿主 Spring Boot 应用。Starter 负责装配引擎、MySQL 持久化、HTTP / JDBC / WEBSERVICE / MAPPING / SPI 系统任务，以及 Servlet 应用中的入站网关、控制台 API 和 MCP。运行时行为与属性定义保留在各自模块。

要求 JDK 21，当前构建基于 Spring Boot 3.4.5。SPI 在线编译需要完整 JDK。Starter 是普通依赖 JAR，不包含应用主类或应用环境配置，也不依赖 `iflow-demo`。

## 添加依赖

```xml
<dependency>
    <groupId>com.helianhealth</groupId>
    <artifactId>iflow-spring-boot-starter</artifactId>
    <version>2.0.0</version>
</dependency>
```

从源码安装到本地 Maven 仓库时，在仓库根目录执行：

```bash
mvn -pl iflow-spring-boot-starter -am clean install
```

此命令不启用 `demo` profile。构建与验证命令为：

```bash
mvn -pl iflow-spring-boot-starter -am clean verify
```

## 初始化数据库与配置

依次执行 [MySQL 建库脚本](init_mysql/01_create_database.sql)和[表结构脚本](init_mysql/02_schema.sql)，再配置宿主应用。

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/iflow?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${IFLOW_DB_USERNAME}
    password: ${IFLOW_DB_PASSWORD}
    hikari:
      maximum-pool-size: 10

agent:
  repository:
    vendor: mysql
```

`agent.repository.vendor=mysql` 选择现有 MySQL DAO。数据源使用标准 `spring.datasource.*` 属性，由 Spring Boot 创建；宿主已有 `DataSource` Bean 时复用该 Bean，已有 `ObjectMapper` Bean 时也直接复用。多数据源应用需要将供 iFlow 使用的数据源标记为 `@Primary`。

宿主保留自己的 `@SpringBootApplication` 即可，无需扫描 `com.helianhealth.iflow`、手动导入内部配置、排除 `DataSourceAutoConfiguration`，或开启 Bean 定义覆盖与循环依赖。自动配置通过 Boot 3 的 `AutoConfiguration.imports` 发现，在数据源与 Jackson 自动配置之后装配。

当前持久化直接使用选定的数据源，宿主数据源中需要存在 iFlow 表。数据库建表由接入方执行，Starter 不自动执行建库与建表脚本。

## 开关与配置

```yaml
iflow:
  enabled: true
  workflow-execution:
    default-timeout-seconds: 60
    task-thread-count: 32
    task-queue-capacity: 256
    sweeper:
      enabled: true
      interval-seconds: 1
  mcp:
    enabled: true
    base-path: /mcp

agent:
  console:
    enabled: true
```

- `iflow.enabled=false` 关闭 Starter 的引擎、任务、持久化 Bean 和 Web 入口装配。宿主 Spring Boot 自身的数据源配置仍由宿主管理。
- `agent.console.enabled=false` 关闭控制台 API，保留引擎、入站网关与 MCP。控制台 JAR 中的静态资源仍按宿主 Spring MVC 的静态资源规则提供。
- `iflow.mcp.enabled=false` 关闭 MCP 入口、会话和 Tool 注册等组件。
- MySQL 工作流定时调度默认启用，Servlet 和非 Web 宿主均可触发已保存的计划。`iflow.scheduler.enabled=false` 可关闭调度，详见[工作流定时调度](workflow-schedule-design.md#启用)。
- 非 Servlet 宿主按上述开关装配运行时组件。网关和控制台等 Web 入口仅在 Servlet 宿主装配。

Servlet 宿主默认提供控制台 `/api/workflow/*` API、Open API 工作流路由、Jarvis 入口和 `/mcp`。控制台资源随 `iflow-console` 提供，由宿主的 Spring MVC 静态资源配置托管；页面入口为宿主 context path 下的 `/`。

属性元数据随属性所属模块发布，支持 IDE 提示。详细行为见 [工作流超时](workflow-timeout.md)、[同步执行](synchronous-workflow-execution.md)、[任务执行策略](task-execution-policy.md)、[JDBC 任务](jdbc-task.md) 和 [SPI 扩展](spi-extensions.md)。

## 替换默认实现

通过宿主的普通 `@Bean` 定义提供 `RestTemplateProvider`、`JdbcConnectionProvider`、`SoapClientProvider`、`QueueDAO`、`ExecutionQueue` 或 `Lock` 时，默认实现会退让。异步执行器使用既有 Bean 名 `asyncTaskExecutor` 进行替换。

自定义系统任务继承 `WorkflowSystemTask`，注册为 Spring Bean 后由 `SystemTaskRegistry` 收集。替换 Starter 提供的协议任务时，将 Bean 名设为对应任务类型，例如：

```java
@Bean(name = "HTTP")
public WorkflowSystemTask customHttpTask() {
    return new CustomHttpTask(); // CustomHttpTask 的任务类型为 HTTP
}
```

自定义 `MetadataDAO` 或 `ExecutionDAO` 时，默认对应 DAO 会退让。默认 MySQL 执行 DAO 通过 `MySqlIndexDAO` 在同一数据库事务内更新索引；替换整套执行存储时，需要同时提供匹配的 `ExecutionDAO` 与 `IndexDAO`。

MySQL 持久化驱动由 `iflow-repository` 独立提供。Starter 默认依赖五个任务模块。若通过 Maven `exclusions` 排除某个 `iflow-*-task` 模块，Starter 会跳过该模块的装配；工作流定义应只使用已注册的任务类型。JDBC 模块携带的数据库驱动见 [JDBC 任务配置](jdbc-task.md)。
