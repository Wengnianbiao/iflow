-- 接口规则工作流表
CREATE TABLE interface_workflow
(
    -- 工作流ID，自增主键，不允许为空、不可更新
    flow_id SERIAL PRIMARY KEY,
    -- 工作流名称，描述工作流的名称信息，允许为空
    flow_name VARCHAR(100) NULL,
    -- 接口URI，上游(Jarvis)调用接口的路由地址，与工作流一对一映射，允许为空
    interface_uri VARCHAR(150) NULL,
    -- 接口参数类型,JSON、XML
    content_type VARCHAR(20) NULL,
    -- 接口入参元数据信息
    content_meta_info VARCHAR(255) NULL,
    -- 工作流首节点，存储起始节点ID列表（逗号分隔的整数字符串），允许为空
    first_flow_nodes VARCHAR(50) NULL,
    -- 状态标识，1-启用，0-禁用，允许为空
    status INTEGER NOT NULL DEFAULT 1
);

-- 工作流节点表
CREATE TABLE interface_workflow_node
(
    -- 节点ID，自增主键，不允许为空、不可更新
    node_id SERIAL PRIMARY KEY,
    -- 节点名称，描述工作流的节点名称信息，允许为空
    node_name VARCHAR(100) NULL,
    -- 工作流ID，这里假设长度根据实际情况设置，允许为空
    flow_id INTEGER NULL,
    -- 参数预处理表达式
    param_filter_expr VARCHAR(255) NULL,
    -- 节点类型，存储枚举对应的字符串值，长度按需调整，不允许为空
    node_type VARCHAR(50) NOT NULL,
    -- api实例元数据，存储JSON格式字符串，用TEXT存储较多内容，允许为空
    meta_info TEXT NULL,
    -- 调度表达式
    schedule_expr VARCHAR(255) NULL,
    -- 拆分字段配置
    split_field VARCHAR(50) NULL,
    -- 调度参数来源
    schedule_param_source_type VARCHAR(25) NULL,
    -- 关键字匹配,默认都用的patientCode
    keyword_field VARCHAR(50) NULL,
    -- SPI拓展字段
    remote_extension VARCHAR(25) NULL
);


-- 工作流节点参数配置表
CREATE TABLE node_param_config
(
    -- 主键ID，自增，不允许为空和更新
    config_id SERIAL PRIMARY KEY,
    -- 所属工作流节点ID（关联到interface_flow_node表的node_id）
    node_id INTEGER NULL,
    -- 上游参数键名
    source_param_key VARCHAR(100) NULL,
    -- 节点类型（object、array、string等，对应ParamType枚举）
    source_param_type VARCHAR(50) NULL,
    -- 三方接口需要的参数类型（对应ParamType枚举）
    target_param_type VARCHAR(50) NULL,
    -- 三方接口需要的参数名称
    target_param_key VARCHAR(100) NULL,
    -- 是否保留单元素键名
    keep_single_element_key BIT NULL,
    -- 父节点ID（顶级节点为NULL）
    parent_id INTEGER NULL,
    -- 排序号（控制参数顺序）
    sort INTEGER NULL DEFAULT 0,
    -- 映射数据源
    mapping_source VARCHAR(50) NULL,
    -- 映射规则类型（对应MappingType枚举）
    mapping_type VARCHAR(50) NULL,
    -- 映射规则内容（固定值、表达式等）
    mapping_rule TEXT NULL,
    -- 参数描述
    param_desc VARCHAR(100) NULL,
    -- 参数处理类型：前置PRE_PROCESS 或 后置POST_PROCESS
    process_type VARCHAR(20) NOT NULL
);


-- 接口调用日志表
CREATE TABLE interface_invoke_log
(
    -- 主键ID，自增，不允许为空和更新
    log_id BIGSERIAL PRIMARY KEY,
    -- 节点名称
    node_name VARCHAR(50) not NULL,
    -- 关键字匹配,默认都用的patientCode
    keyword VARCHAR(50) NULL,
    -- 业务数据
    business_data TEXT,
    -- 请求参数
    param_before_invoke TEXT,
    -- 远程调用结果
    remote_invoke_response TEXT,
    -- 响应参数
    param_after_invoke TEXT,
    -- 调用时间
    invoke_time VARCHAR(20) NULL,
    -- 创建时间
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Mock配置表
CREATE TABLE mock_config
(
    -- 主键ID，自增
    mock_id SERIAL PRIMARY KEY,
    -- 节点ID（关联到interface_workflow_node表的node_id）
    node_id INTEGER NOT NULL,
    -- Mock场景名称（用于区分不同的mock case）
    case_name VARCHAR(100) NOT NULL,
    -- Mock场景描述
    case_desc VARCHAR(255) NULL,
    -- Mock响应数据（JSON格式）
    mock_response TEXT NOT NULL
);

-- 节点扩展配置表（SPI动态扩展）
CREATE TABLE node_extension_config
(
    -- 主键ID，自增
    code_id SERIAL PRIMARY KEY,
    -- 关联的节点ID（可选，用于自动配置节点的remote_extension字段）
    node_id INTEGER NULL,
    -- SPI接口（全限定名），例如: com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker
    spi_interface VARCHAR(255) NOT NULL,
    -- 扩展名称（用于SPI配置的key），例如: custom-http
    extension_name VARCHAR(100) NOT NULL,
    -- 实现类全限定名，例如: com.helianhealth.agent.custom.CustomRemoteInvoker
    class_name VARCHAR(255) NOT NULL,
    -- Java源代码
    source_code TEXT NULL,
    -- 代码描述
    description VARCHAR(500) NULL,
    -- 是否启用 1-启用 0-禁用
    enabled INTEGER NOT NULL DEFAULT 1,
    -- 扩展方式：PROJECT-项目扩展（业务项目中实现） ONLINE-在线扩展（页面编写） 必选
    extension_mode VARCHAR(20) NOT NULL DEFAULT 'ONLINE'
);


