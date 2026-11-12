# iFlow

**AI 驱动的零边际成本集成中间件** —— 一个基于 Spring Boot 的可插拔工作流引擎，支持可视化配置，让接口集成像搭积木一样简单。

## ✨ 核心价值

- 🤖 **AI 协作配置**：通过 AI Agent 自动理解接口文档，生成工作流配置
- 🔌 **可插拔设计**：作为 Spring Boot Starter 集成，提供本地 demo 用于调试和演示
- 🎨 **可视化编排**：无需编码，通过配置页面即可完成接口集成
- 🔄 **多协议支持**：HTTP、数据库、WebService 等多种调用方式
- 🧪 **Mock 仿真**：内置 Mock 功能，支持接口测试与联调

## 📦 模块结构

```
iflow/
├── iflow-common              # 基础设施层（通用工具类、枚举、异常定义）
├── iflow-repository          # 数据访问层（厂商 DAO、领域模型）
├── iflow-core                # 工作流核心（执行器、Decider、系统任务抽象）
├── iflow-gateway              # 入站网关（Open API / Jarvis / HL7 协议暴露与路由）
├── iflow-console             # 控制台服务层（后台管理接口）
├── iflow-ai                  # AI智能代理层（MCP接口、AI配置专家）
├── iflow-spring-boot-starter # Spring Boot Starter（自动配置、属性绑定）
└── iflow-demo                # 本地调试与演示应用（通过 -Pdemo 按需构建）
```

## 🚀 快速开始

### 环境要求

- JDK 21（编译与运行）；SPI 在线扩展依赖 JDK 编译器，运行时也应使用完整 JDK。
- 作为 Starter 集成时，宿主应用同样需要使用 Java 21 或更高版本。
- Docker 默认使用 `eclipse-temurin:21-jdk-jammy`，自定义 `BASE_IMAGE` 时也需提供 JDK 21 或更高版本。

### 默认项目构建

```bash
# 构建正式模块，不包含 demo
mvn clean package -Dmaven.test.skip=true
```

`iflow-demo` 的源码、配置和 POM 正常纳入 Git 管理，通过 Maven `demo` profile 按需加入构建，demo 制品不发布到 Maven 仓库。

### 方式一：本地 demo 调试

```bash
# 编译打包 demo 及其依赖模块
mvn -Pdemo -pl iflow-demo -am clean package -Dmaven.test.skip=true

# 初始化 MySQL 数据库
mysql -u root -p < docs/init_mysql/01_create_database.sql
mysql -u root -p iflow < docs/init_mysql/02_schema.sql

# 运行 demo（默认使用 Spring local profile）
java -jar iflow-demo/target/iflow.jar

# 访问配置页面
http://localhost:8099
```

在 IDEA 的 Maven 面板启用 `demo` profile 并重新加载项目后，即可运行或调试 `com.helianhealth.iflow.persistence.IflowApplication`。Maven `demo` profile 只控制模块是否加入构建，与 Spring 运行环境 profile 独立。

### 方式二：作为 Starter 集成

**1. 添加依赖**

```xml
<dependency>
    <groupId>com.helianhealth</groupId>
    <artifactId>iflow-spring-boot-starter</artifactId>
    <version>2.0.0</version>
</dependency>
```

**2. 配置数据源**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/iflow
    username: ${IFLOW_DB_USERNAME}
    password: ${IFLOW_DB_PASSWORD}

agent:
  repository:
    vendor: mysql
```

**3. 启动应用**

先执行上面的四条 MySQL 初始化命令，再启动宿主 Spring Boot 应用。Starter 自动装配引擎、五类系统任务、入站网关、控制台、定时调度和 MCP，无需额外包扫描或手动导入配置。控制台页面使用宿主应用的地址和 context path。

默认构建和 Starter 依赖均不包含 demo。开关、宿主 Bean 替换及任务模块裁剪见 [Spring Boot Starter 接入](docs/spring-boot-starter.md)。

### Docker 演示

手动执行以下命令，按需构建 demo 镜像和导出包：

```bash
./docker/build-image.sh
```

该脚本显式启用 Maven `demo` profile，普通 Maven 构建不会构建 demo 镜像。

## 🏗️ 架构设计

### 模块依赖关系

```
iflow-common (基础层)
    ↑
    ├── iflow-repository
    ├── iflow-core
    ├── iflow-gateway
    ├── iflow-console
    ├── iflow-ai
    └── iflow-spring-boot-starter
```

`iflow-demo` 按需装配运行所需模块，作为本地调试与演示入口，不属于默认构建模块。

### 核心特性

- **模块化分层架构**：基础层（common）→ 数据层（repository）→ 核心层（core）→ 服务层（gateway、console、ai）→ 自动配置（starter）
- **SPI动态扩展机制**：支持SPI动态注册扩展，可插拔调用客户端，支持 HTTP、WebService、JDBC 等协议，详见 [SPI 扩展接入与命名迁移](docs/spi-extensions.md)
- **JDBC任务**：模块默认携带 MySQL、PostgreSQL、SQL Server、Oracle、openGauss、Kingbase 驱动，编辑器按厂商切换连接 URL 模板，详见 [JDBC 任务配置](docs/jdbc-task.md)
- **配置化驱动**：工作流配置存储在数据库，支持动态修改无需重启，参数映射支持 SpEL 表达式
- **AI智能协作**：内置MCP接口，支持AI Agent自动理解接口文档并生成配置
- **可视化管理**：提供 React 前端控制台，支持工作流可视化编排和参数配置
- **内存异步 Worker**：支持以 Spring Bean 注册 Conductor 风格 Worker；任务仅在当前进程执行，停机终止且不恢复，详见 [接入说明](docs/in-memory-worker.md)

## 🤖 AI 协作配置

本项目支持通过 AI Agent 自动生成接口配置，采用**文档驱动 + 配置化**的设计理念：

```
┌─────────────────────────┐
│  接口文档               │
│  (API Specification)   │
└───────────┬─────────────┘
            ↓
┌─────────────────────────┐
│  AI Agent              │
│  理解文档 → 生成配置    │
└───────────┬─────────────┘
            ↓
┌─────────────────────────┐
│  iFlow       │
│  工作流 + 节点 + 映射   │
└─────────────────────────┘
```

### 核心优势

- **文档驱动**：将接口配置规则显式化，AI 可直接理解和执行
- **零编码集成**：通过配置即可完成接口对接，无需编写代码
- **工具无关**：支持任何 Coding Agent（Qoder、Cursor、Claude 等）

### AI 对接方式

AI Agent 通过 **MCP Streamable HTTP**（规范 `2025-11-25`，`POST /mcp`，JSON-RPC 2.0）统一操作配置；人工管理走控制台 API（`/api/workflow/*`）。

握手后 Server 会返回 `MCP-Session-Id` 与 `MCP-Protocol-Version` 响应头；notification（如 `notifications/initialized`）返回 `202 Accepted`。

MCP Server 与 Spring 同进程托管，由 starter 默认开启，可通过 `iflow.mcp.enabled=false` 关闭：

```yaml
iflow:
  mcp:
    enabled: true              # 是否启用 MCP 入口
    base-path: /mcp            # Streamable HTTP 端点
    protocol-version: 2025-11-25
    session-enabled: true      # initialize 时分配 MCP-Session-Id
    strict-session: false      # 是否强制后续请求携带 Session
    server-name: iflow
    server-version: 1.0.0
```

当前 MCP 注册 `agent_health` Tool，通过 `action=ping` 检查进程，或通过 `action=check` 检查数据源。工作流定义、执行查询与统计使用控制台 `/api/workflow/*` API。

---

## 📄 许可证

Copyright © 2025
