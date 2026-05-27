# Interface Agent

**AI 驱动的零边际成本集成中间件** —— 一个基于 Spring Boot 的可插拔工作流引擎，支持可视化配置，让接口集成像搭积木一样简单。

## ✨ 核心价值

- 🤖 **AI 协作配置**：通过 AI Agent 自动理解接口文档，生成工作流配置
- 🔌 **可插拔设计**：既可独立部署，也可作为 Spring Boot Starter 集成
- 🎨 **可视化编排**：无需编码，通过配置页面即可完成接口集成
- 🔄 **多协议支持**：HTTP、数据库、WebService 等多种调用方式
- 🧪 **Mock 仿真**：内置 Mock 功能，支持接口测试与联调

## 📦 模块结构

```
interface-agent/
├── agent-common              # 基础设施层（通用工具类、枚举、异常定义）
├── agent-repository          # 数据访问层（MyBatis Mapper、DAO、领域模型）
├── agent-invocation          # 远程调用层（协议支持、参数处理、调用执行链）
├── agent-schedule            # 调度引擎（定时任务、工作流调度）
├── agent-api                 # 开放接口层（REST API、JSON响应处理）
├── agent-console             # 控制台服务层（后台管理接口）
├── agent-ai                  # AI智能代理层（MCP接口、AI配置专家）
├── agent-spring-boot-starter # Spring Boot Starter（自动配置、属性绑定）
└── agent-web                 # Web应用入口（主启动类、静态资源配置）
```

## 🚀 快速开始

### 方式一：独立运行

```bash
# 编译打包
mvn clean package -Dmaven.test.skip=true

# 初始化数据库（选择对应数据库类型）
doc/init_mysql.sql        # MySQL
doc/init_sqlserver.sql    # SQL Server
doc/init_gauss.sql        # OpenGauss

# 运行应用
java -jar agent-web/target/interface-agent.jar

# 访问配置页面
http://localhost:8080
```

### 方式二：作为 Starter 集成

**1. 添加依赖**

```xml
<dependency>
    <groupId>com.helianhealth</groupId>
    <artifactId>agent-spring-boot-starter</artifactId>
    <version>1.0.23</version>
</dependency>
```

**2. 配置数据源**

```yaml
spring:
  datasource:
    agent:
      url: jdbc:mysql://localhost:3306/interface_agent
      username: root
      password: your_password
```

**3. 启动应用**

启动你的 Spring Boot 应用，工作流引擎会自动初始化，访问 `http://localhost:8080` 即可使用配置页面。

## 🏗️ 架构设计

### 模块依赖关系

```
agent-common (基础层)
    ↑
    ├── agent-repository
    ├── agent-invocation
    │       ↑
    │       └── agent-schedule
    ├── agent-api
    ├── agent-console
    ├── agent-ai
    └── agent-spring-boot-starter
            ↑
            └── agent-web (应用层)
```

### 核心特性

- **模块化分层架构**：基础层（common）→ 数据层（repository）→ 核心层（invocation、schedule）→ 服务层（api、console、ai）→ 应用层（web）
- **SPI动态扩展机制**：支持SPI动态注册扩展，可插拔调用客户端，支持 HTTP、WebService、JDBC 等协议
- **配置化驱动**：工作流配置存储在数据库，支持动态修改无需重启，参数映射支持 SpEL 表达式
- **AI智能协作**：内置MCP接口，支持AI Agent自动理解接口文档并生成配置
- **可视化管理**：提供Vue.js前端控制台，支持工作流可视化编排和参数配置

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
│  Interface Agent       │
│  工作流 + 节点 + 映射   │
└─────────────────────────┘
```

### 核心优势

- **文档驱动**：将接口配置规则显式化，AI 可直接理解和执行
- **零编码集成**：通过配置即可完成接口对接，无需编写代码
- **工具无关**：支持任何 Coding Agent（Qoder、Cursor、Claude 等）

### AI 对接方式

AI Agent 通过 **MCP Streamable HTTP**（规范 `2025-11-25`，`POST /mcp`，JSON-RPC 2.0）统一操作配置；人工管理走控制台 API（`/v1/console/*`）。

握手后 Server 会返回 `MCP-Session-Id` 与 `MCP-Protocol-Version` 响应头；notification（如 `notifications/initialized`）返回 `202 Accepted`。

MCP Server 与 Spring 同进程托管，由 starter 默认开启，可通过 `interface-agent.mcp.enabled=false` 关闭：

```yaml
interface-agent:
  mcp:
    enabled: true              # 是否启用 MCP 入口
    base-path: /mcp            # Streamable HTTP 端点
    protocol-version: 2025-11-25
    session-enabled: true      # initialize 时分配 MCP-Session-Id
    strict-session: false      # 是否强制后续请求携带 Session
    server-name: interface-agent
    server-version: 1.0.0
```

8 个域级 Tool：`agent_health` / `agent_workflow` / `agent_workflow_node` / `agent_node_param_config` /
`agent_mock_config` / `agent_mock_invoke` / `agent_invoke_log` / `agent_spi_extension`，
每个 Tool 用 `action` 字段区分 CRUD 操作。

---

## 📄 许可证

Copyright © 2025
