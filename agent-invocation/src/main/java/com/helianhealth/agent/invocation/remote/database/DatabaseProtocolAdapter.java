package com.helianhealth.agent.invocation.remote.database;

import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;


@Slf4j
public class DatabaseProtocolAdapter implements InterfaceClientProxy {

    private DatabaseSqlHandler databaseSqlHandler;

    /**
     * 无参构造函数，用于SPI机制实例化
     * DatabaseSqlHandler将通过ExtensionFactory自动注入（Setter注入）
     */
    public DatabaseProtocolAdapter() {
    }

    /**
     * 带参构造函数，支持手动注入依赖
     */
    public DatabaseProtocolAdapter(DatabaseSqlHandler databaseSqlHandler) {
        this.databaseSqlHandler = databaseSqlHandler;
    }

    /**
     * Setter注入方法，用于ExtensionFactory自动注入
     */
    public void setDatabaseSqlHandler(DatabaseSqlHandler databaseSqlHandler) {
        this.databaseSqlHandler = databaseSqlHandler;
    }

    @Override
    public Map<String, Object> doInvoke(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {
        try {
            // 构建SQL语句
            String sql = databaseSqlHandler.resolveParamNodes(flowNode, params);
            // 执行SQL并转化为Map
            return databaseSqlHandler.invokeSqlAndConvertResult(flowNode, sql);
        } catch (Exception e) {
            log.error("数据库调用失败", e);
            throw new RuntimeException("数据库调用失败: " + e.getMessage(), e);
        }
    }

}
