# JDBC 任务配置

引入 `iflow-jdbc-task` 后，模块会通过 Maven 运行时依赖携带以下六种数据库驱动，宿主应用无需逐个添加驱动 JAR。版本沿用项目父 POM 与 Spring Boot BOM 的管理。

| 数据库厂商 | 驱动类 | 编辑器 URL 模板 |
|------------|--------|-----------------|
| MYSQL | `com.mysql.cj.jdbc.Driver` | `jdbc:mysql://localhost:3306/demo` |
| POSTGRES | `org.postgresql.Driver` | `jdbc:postgresql://localhost:5432/demo` |
| SQLSERVER | `com.microsoft.sqlserver.jdbc.SQLServerDriver` | `jdbc:sqlserver://localhost:1433;databaseName=demo` |
| ORACLE | `oracle.jdbc.OracleDriver` | `jdbc:oracle:thin:@//localhost:1521/orcl` |
| OPENGAUSS | `org.opengauss.Driver` | `jdbc:opengauss://localhost:5432/demo` |
| KINGBASE | `com.kingbase8.Driver` | `jdbc:kingbase8://localhost:54321/demo` |

新建 JDBC 节点默认使用 MYSQL。手动切换数据库厂商时，编辑器同时更新 `inputParameters.vendor` 和 `inputParameters.url`，将 URL 替换为对应模板；随后填写实际主机、端口、数据库名或 Oracle 服务名，按需补充连接参数。用户名、密码、SQL 模板和 SQL 模板参数保持原值。

打开已有节点或再次选择当前厂商时，保留已填写的 URL，包括自定义连接参数和工作流变量表达式。URL 模板只用于编辑器辅助填写，运行时仍使用节点保存的完整 URL，并根据 `vendor` 加载驱动，每次任务独立建连。

URL 格式可参阅厂商文档：[MySQL](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-jdbc-url-format.html)、[PostgreSQL](https://jdbc.postgresql.org/documentation/use/)、[SQL Server](https://learn.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url)、[Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/19/jjdbc/data-sources-and-URLs.html)、[openGauss](https://docs.opengauss.org/en/docs/7.0.0/developer_guide/connecting_to_a_database_jdbc.html)、[Kingbase](https://docs.kingbase.com.cn/cn/KES-V9R2C13/application/client_interface/Java/Jdbc/jdbc-2/)。
