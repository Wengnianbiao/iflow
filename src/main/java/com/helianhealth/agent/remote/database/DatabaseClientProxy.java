package com.helianhealth.agent.remote.database;

import com.helianhealth.agent.enums.MappingType;
import com.helianhealth.agent.enums.ParamType;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNodeDO;
import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.InterfaceClientProxy;
import com.helianhealth.agent.remote.ParamNodeHandler;
import com.helianhealth.agent.remote.ProxyConvertHelper;
import com.helianhealth.agent.utils.JsonUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public class DatabaseClientProxy implements InterfaceClientProxy {

    @Setter
    private DatabaseSqlHandler databaseSqlHandler_findBySpring;

    @Override
    public Map<String, Object> doInvoke(InterfaceWorkflowNodeDO flowNode, List<ParamTreeNode> params) {
        try {
            // 构建SQL语句
            String sql = databaseSqlHandler_findBySpring.resolveParamNodes(flowNode, params);
            // 执行SQL并转化为Map
            return databaseSqlHandler_findBySpring.invokeSqlAndConvertResult(flowNode, sql);
        } catch (Exception e) {
            log.error("数据库调用失败", e);
            throw new RuntimeException("数据库调用失败: " + e.getMessage(), e);
        }
    }


}
