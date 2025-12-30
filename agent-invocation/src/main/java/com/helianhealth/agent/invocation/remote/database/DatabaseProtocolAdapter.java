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

    @Setter
    private DatabaseSqlHandler databaseSqlHandler;

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
