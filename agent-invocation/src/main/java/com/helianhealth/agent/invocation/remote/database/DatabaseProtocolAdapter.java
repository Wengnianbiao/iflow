package com.helianhealth.agent.invocation.remote.database;

import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentInvocationException;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.remote.AbstractProtocolAdapter;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;

import java.util.List;
import java.util.Map;

/**
 * 数据库协议适配器
 */
public class DatabaseProtocolAdapter extends AbstractProtocolAdapter {

    private final DatabaseSqlHandler databaseSqlHandler;

    public DatabaseProtocolAdapter(DatabaseSqlHandler databaseSqlHandler) {
        this.databaseSqlHandler = databaseSqlHandler;
    }

    @Override
    protected String resolveParam(WorkflowNode flowNode, List<ParamTreeNode> params) {
        return databaseSqlHandler.resolveParamNodes(flowNode, params);
    }

    @Override
    protected String executeInvoke(WorkflowNode flowNode, String resolvedParam) {
        return databaseSqlHandler.executeSqlAndGetRawResponse(flowNode, resolvedParam);
    }

    @Override
    protected Map<String, Object> parseResponse(WorkflowNode flowNode, String rawResponse) {
        try {
            return JsonUtils.toMap(rawResponse);
        } catch (Exception e) {
            throw new AgentInvocationException(AgentErrorCode.REMOTE_RESPONSE_PARSE_ERROR, "数据库响应解析失败: " + e.getMessage(), e);
        }
    }
}
