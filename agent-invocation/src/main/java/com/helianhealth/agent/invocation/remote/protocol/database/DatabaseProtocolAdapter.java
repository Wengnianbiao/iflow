package com.helianhealth.agent.invocation.remote.protocol.database;

import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.remote.core.AbstractProtocolAdapter;
import com.helianhealth.agent.invocation.remote.param.model.ParamTreeNode;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;

import java.util.List;
import java.util.Map;

/**
 * 数据库协议适配器
 */
public class DatabaseProtocolAdapter extends AbstractProtocolAdapter {

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
    protected String resolveParams(WorkflowNode flowNode, List<ParamTreeNode> params) {
        return databaseSqlHandler.resolveParamNodes(flowNode, params);
    }

    @Override
    protected String executeInvoke(WorkflowNode flowNode, String resolvedParam) {
        return databaseSqlHandler.executeSqlAndGetRawResponse(flowNode, resolvedParam);
    }

    @Override
    protected Map<String, Object> parseResponse(WorkflowNode flowNode, String rawResponse) {
        return JsonUtils.toMap(rawResponse);
    }
}
