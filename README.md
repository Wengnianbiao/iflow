# Interface Agent - 工作流引擎

一个基于Spring Boot的可插拔工作流引擎，支持HTTP、数据库、WebService等多种调用方式，提供可视化配置界面。

> **两种使用方式**：既可以直接运行本项目的 `agent-web` 模块作为独立应用，也可以将 `agent-spring-boot-starter` 作为 Starter 引入其他 Spring Boot 项目使用。

## 项目特点

- 🔌 **可插拔设计**：既可独立部署运行，也可作为 Spring Boot Starter 集成到任何 Spring Boot 项目
- 🎨 **可视化配置**：提供前端配置页面，无需编码即可配置工作流
- 🔄 **多种调用方式**：支持HTTP、数据库、WebService等多种远程调用
- 📊 **动态参数映射**：灵活的参数映射和转换机制
- 🧪 **Mock支持**：内置Mock功能，方便测试
- 🔧 **动态扩展**：支持动态SPI机制，运行时加载扩展

## 模块结构

```
interface-agent/
├── agent-common              # 通用工具和基础组件
├── agent-workflow            # 工作流配置管理
├── agent-invocation          # 远程调用实现
├── agent-schedule            # 调度引擎
├── agent-api                 # 开放API接口
├── agent-log                 # 日志管理
├── agent-mock                # Mock数据管理
├── agent-config              # 公共配置（CORS、异常处理、静态资源等）
├── agent-spring-boot-starter # Spring Boot 自动配置 Starter
└── agent-web                 # Web 应用与前端页面（可执行入口）
```

## 快速开始（以 Starter 集成方式为例）

如果你希望在自己的 Spring Boot 项目中集成工作流引擎，可以按以下步骤引入 `agent-spring-boot-starter`：

### 1. 添加依赖

在你的Spring Boot项目的 \`pom.xml\` 中添加：

\`\`\`xml
<dependency>
    <groupId>com.helianhealth</groupId>
    <artifactId>agent-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
\`\`\`

### 2. 配置数据源

在你的 \`application.yml\` 中配置数据源：

\`\`\`yaml
spring:
  datasource:
    # agent元数据数据源（必须）
    agent:
      type: com.alibaba.druid.pool.DruidDataSource
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/interface_agent
      username: root
      password: your_password
      
    # 业务数据源（可选）
    hems:
      type: com.alibaba.druid.pool.DruidDataSource
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/your_business_db
      username: root
      password: your_password

pagehelper:
  helper-dialect: mysql  # 根据agent数据源类型选择
\`\`\`

### 3. 初始化数据库

执行数据库初始化脚本（根据你使用的数据库选择）：

\`\`\`bash
doc/init_mysql.sql        # MySQL
doc/init_sqlserver.sql    # SQL Server
doc/init_gauss.sql        # OpenGauss
\`\`\`

### 4. 启动应用

启动你的Spring Boot应用，工作流引擎会自动初始化：

\`\`\`java
@SpringBootApplication
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
\`\`\`

启动日志会显示：
\`\`\`
Interface Agent Auto Configuration initialized!
工作流引擎已启动，可以使用前端配置页面和API调用
\`\`\`

### 5. 访问配置页面

启动后访问：\`http://localhost:8080\`（端口根据你的应用配置）

## 使用方式

### 方式一：前端配置

1. 访问前端配置页面
2. 创建工作流
3. 配置节点和参数映射
4. 启用工作流

### 方式二：API调用

在你的代码中注入调度器并调用：

\`\`\`java
@RestController
public class YourController {
    
    @Autowired
    private WorkFlowEngineScheduler scheduler;
    
    @PostMapping("/execute")
    public Map<String, Object> execute(@RequestBody Map<String, Object> businessData) {
        // 执行工作流
        return scheduler.execute(workflowId, businessData);
    }
}
\`\`\`

## 配置说明

### 启用/禁用

默认启用，可通过配置禁用：

\`\`\`yaml
interface-agent:
  enabled: false  # 禁用工作流引擎
\`\`\`

### 数据库支持

- MySQL 5.7+
- SQL Server 2012+
- Oracle 12c+
- OpenGauss 3.0+

### 节点类型

- **HTTP节点**：调用HTTP/HTTPS接口
- **数据库节点**：执行SQL查询
- **WebService节点**：调用SOAP服务

## 核心API接口

### 工作流管理

\`\`\`
GET  /v1/console/workflow/all           # 获取工作流列表
POST /v1/console/workflow/create        # 创建工作流
POST /v1/console/workflow/update        # 更新工作流
DELETE /v1/console/workflow/{id}        # 删除工作流
\`\`\`

### 节点管理

\`\`\`
GET  /v1/console/node/all               # 获取节点列表
POST /v1/console/node/create            # 创建节点
GET  /v1/console/node/{nodeId}          # 获取节点详情
POST /v1/console/node/update            # 更新节点
DELETE /v1/console/node/{nodeId}        # 删除节点
\`\`\`

### 调度执行

\`\`\`
POST /api/schedule/execute              # 执行工作流
\`\`\`

## 开发指南

### 本地开发

\`\`\`bash
# 克隆项目
git clone <repository-url>
cd interface-agent-dev

# 编译安装到本地Maven仓库
mvn clean install -DskipTests

# 在你的项目中引用
\`\`\`

### 模块化架构说明

#### 模块结构

```
interface-agent/
├── agent-common/              # 共享基础设施（异常、工具类、公共常量与枚举、注解）
├── agent-workflow/            # 工作流配置与管理
├── agent-invocation/          # 远程调用模块（HTTP、WebService、数据库等）
├── agent-schedule/            # 调度模块（工作流调度、节点分发）
├── agent-api/                 # 对外API封装
├── agent-log/                 # 调用日志记录与查询
├── agent-mock/                # Mock配置与数据管理
├── agent-config/              # 跨模块通用配置（CORS、异常处理、静态资源等）
├── agent-spring-boot-starter/ # Spring Boot 启动器与自动配置
└── agent-web/                 # Web应用模块（前端页面、应用入口）
```

#### 模块依赖关系

```
agent-common (基础层)
    ↑
    ├── agent-workflow
    ├── agent-invocation
    ├── agent-api
    ├── agent-log
    └── agent-mock
            ↑
            └── agent-schedule
                    ↑
                    └── agent-config
                            ↑
                            └── agent-spring-boot-starter
                                    ↑
                                    └── agent-web (应用层)
```

#### 核心特性

- 模块化分层：基础层（agent-common）→ 核心层（workflow、invocation、schedule）→ 功能层（api、log、mock）→ 启动层（agent-spring-boot-starter）→ 应用层（agent-web）。
- SPI扩展机制：使用 Dubbo SPI 实现可插拔的远程调用客户端，支持 HTTP、WebService、JDBC 等多种协议，可通过配置或代码动态扩展。
- 配置化设计：工作流节点配置存储在数据库，支持动态修改流程无需重启，参数映射支持 SpEL 表达式。

#### 构建与运行

```bash
mvn clean package -Dmaven.test.skip=true

java -jar agent-web/target/interface-agent.jar
```

#### 命名规范与模块职责

- 模块命名统一为 `agent-xxx`，其中 `agent-spring-boot-starter` 符合 Spring Boot Starter 规范。
- 删除冗余的 `agent-service`、`agent-model` 等历史模块，仅保留职责清晰的功能模块。
- 打包后的可执行 Jar 统一命名为 `interface-agent.jar`（位于 `agent-web` 模块）。
- `agent-spring-boot-starter`：提供自动配置能力，对外作为 Starter 暴露。
- `agent-web`：作为应用层入口，主要承载前端静态资源和运行配置，包含 Spring Boot 启动类。

- 最后更新：2025-12-16
- 架构版本：2.0（模块化重构）

## 技术栈

- Spring Boot 2.3.2.RELEASE
- MyBatis + PageHelper
- Druid连接池
- Dubbo SPI机制
- Quartz调度
- SpEL 表达式引擎
- Swagger API 文档
- FastJSON 2.0
- Vue.js（前端）

## AI 协作配置

本项目支持通过 AI Agent 自动生成接口配置，采用**文档驱动 + 配置化**的设计理念：

```
┌─────────────────────────────────────┐
│  文档驱动（领域知识显式化）           │
│  interface-config-guide-ai.md       │
│  - 节点配置规则                      │
│  - 参数映射规则                      │
│  - 嵌套结构处理                      │
│  - 特殊场景处理                      │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  AI Agent（执行器）                  │
│  任何 Coding Agent                  │
│  - 理解文档                          │
│  - 生成配置                          │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  配置化（数据驱动）                   │
│  MCP API + JSON 配置                │
│  - 节点配置 JSON                     │
│  - 工作流配置 JSON                   │
│  - 参数映射配置 JSON                 │
└─────────────────────────────────────┘
```

### 核心特性

- **文档驱动**：将接口配置规则显式化为 Markdown 文档，AI 可直接理解和执行
- **配置化**：所有配置以声明式 JSON 格式存储，易于生成和维护
- **工具无关**：支持任何 Coding Agent（Qoder、Cursor、Claude 等）
- **人类可审查**：配置规则清晰可见，便于审查和版本管理

### 使用方式

1. 准备三方接口文档
2. 告知 AI Agent 严格遵循 `interface-config-guide-ai.md` 中的规则
3. AI 自动生成节点配置、工作流配置和参数映射
4. 通过 MCP API 创建配置到系统中

详细说明请参考：[接口配置 AI 指南](interface-config-guide-ai.md)

---

## 文档

- [模块化架构说明](模块化架构说明.md)
- [接口配置 AI 指南](interface-config-guide-ai.md)

## 许可证

Copyright © 2025
