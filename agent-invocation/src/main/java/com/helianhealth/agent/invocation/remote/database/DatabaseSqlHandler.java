package com.helianhealth.agent.invocation.remote.database;

import com.alibaba.fastjson2.JSONArray;
import com.helianhealth.agent.common.base.MetaInfoKeyConstants;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.exception.AgentConfigException;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentInvocationException;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.ParamResolver;
import com.helianhealth.agent.invocation.remote.manager.DatabaseApiClientManager;
import com.helianhealth.agent.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DatabaseSqlHandler implements ParamResolver {

    private final DatabaseApiClientManager clientManager;

    public DatabaseSqlHandler() {
        this.clientManager = DatabaseApiClientManager.getInstance();
    }

    @Override
    public String resolveParamNodes(WorkflowNode flowNode, List<ParamTreeNode> params) {
        Map<String, Object> metaInfo = JsonUtils.parseMetaInfo(flowNode.getMetaInfo());
        String sqlTemplate = (String) metaInfo.get(MetaInfoKeyConstants.DB_SQL_TEMPLATE);
        String operation = (String) metaInfo.get(MetaInfoKeyConstants.DB_OPERATION);

        // 检查是否有SQL模板
        if (!StringUtils.hasText(sqlTemplate)) {
            throw new AgentConfigException(AgentErrorCode.SQL_TEMPLATE_MISSING);
        }
        if (!StringUtils.hasText(operation)) {
            throw new AgentConfigException(AgentErrorCode.SQL_OPERATION_MISSING);
        }

        switch (operation.toUpperCase()) {
            case DatabaseSqlConstants.OPERATION_INSERT:
                // 构建INSERT语句一定是对象类型或数组类型包裹
                return buildInsertSql(sqlTemplate, params.get(0));
            case DatabaseSqlConstants.OPERATION_UPDATE:
                return buildUpdateSql(sqlTemplate, params);
            case DatabaseSqlConstants.OPERATION_DELETE:
                return buildDeleteSql(sqlTemplate, params);
            case DatabaseSqlConstants.OPERATION_SELECT:
            default:
                return buildSelectSql(sqlTemplate, params);
        }
    }

    /**
     * 构建UPDATE SQL语句
     * 遍历所有参数节点，替换SQL模板中的占位符
     * 例如: UPDATE user SET name={name}, age={age} WHERE id={id}
     */
    private String buildUpdateSql(String sqlTemplate, List<ParamTreeNode> params) {
        String finalSql = sqlTemplate;

        // 遍历所有参数节点
        for (ParamTreeNode param : params) {
            // 只处理有参数值的节点
            if (param.getParamValue() != null) {
                String formattedValue = formatValue(param.getParamValue());
                finalSql = finalSql.replace(DatabaseSqlConstants.PLACEHOLDER_START
                        + param.getParamKey() + DatabaseSqlConstants.PLACEHOLDER_END, formattedValue);
            }
        }

        // 检查是否还有未替换的占位符
        if (finalSql.contains(DatabaseSqlConstants.PLACEHOLDER_START) && finalSql.contains(DatabaseSqlConstants.PLACEHOLDER_END)) {
            throw new AgentConfigException(AgentErrorCode.SQL_PLACEHOLDER_UNMATCHED, "SQL语句中存在未匹配的参数占位符: " + finalSql);
        }

        return finalSql;
    }

    private String buildDeleteSql(String sqlTemplate, List<ParamTreeNode> params) {
        String finalSql = sqlTemplate;
        // 遍历所有参数节点
        for (ParamTreeNode param : params) {
            // 只处理有参数值的节点
            if (param.getParamValue() != null) {
                String formattedValue = formatValue(param.getParamValue());
                finalSql = finalSql.replace(DatabaseSqlConstants.PLACEHOLDER_START
                        + param.getParamKey() + DatabaseSqlConstants.PLACEHOLDER_END, formattedValue);
            }
        }

        // 检查是否还有未替换的占位符
        if (finalSql.contains(DatabaseSqlConstants.PLACEHOLDER_START) && finalSql.contains(DatabaseSqlConstants.PLACEHOLDER_END)) {
            throw new AgentConfigException(AgentErrorCode.SQL_PLACEHOLDER_UNMATCHED, "SQL语句中存在未匹配的参数占位符: " + finalSql);
        }

        return finalSql;
    }

    /**
     * 构建INSERT SQL语句(接口场景只存在对象和数组类型参数,单个字段的INSERT也需要封装成对象)
     */
    private String buildInsertSql(String sqlTemplate, ParamTreeNode paramNode) {
        // 检查是否包含ON DUPLICATE KEY UPDATE部分
        int onUpdateIndex = sqlTemplate.toUpperCase().indexOf(DatabaseSqlConstants.SQL_KEYWORD_ON_DUPLICATE_KEY_UPDATE);
        String valuesPart;
        String updatePart = "";

        if (onUpdateIndex != -1) {
            // 分离VALUES部分和ON DUPLICATE KEY UPDATE部分
            valuesPart = sqlTemplate.substring(0, onUpdateIndex).trim();
            updatePart = sqlTemplate.substring(onUpdateIndex);
        } else {
            // 没有ON DUPLICATE KEY UPDATE部分,直接使用整个模板
            valuesPart = sqlTemplate;
        }

        // 提取VALUES部分中的模板
        int valuesStart = valuesPart.indexOf(DatabaseSqlConstants.SQL_KEYWORD_VALUES) + DatabaseSqlConstants.SQL_KEYWORD_VALUES.length();
        String valueTemplate = valuesPart.substring(valuesStart).trim();
        String prefix = valuesPart.substring(0, valuesStart).trim();

        StringBuilder valuesBuilder = new StringBuilder();
        boolean hasRecords = false;
        int recordCount = 0;

        // 处理数组/纯数组类型
        if (paramNode.getParamType() == ParamType.ARRAY || paramNode.getParamType() == ParamType.PURE_ARRAY) {
            if (paramNode.getChildren() != null) {
                for (ParamTreeNode childNode : paramNode.getChildren()) {
                    // 只处理对象类型的子节点，且对象必须包含字段信息
                    if (childNode.getParamType() == ParamType.OBJECT && childNode.getChildren() != null && !childNode.getChildren().isEmpty()) {
                        hasRecords = true;
                        // 替换当前记录的占位符
                        String recordValues = valueTemplate;
                        for (ParamTreeNode field : childNode.getChildren()) {
                            String formattedValue = formatValue(field.getParamValue());
                            recordValues = recordValues.replace("{" + field.getParamKey() + "}", formattedValue);
                        }
                        // 添加分隔符
                        if (recordCount > 0) {
                            valuesBuilder.append(", ");
                        }
                        valuesBuilder.append(recordValues);
                        recordCount++;
                    }
                }
            }
        } else if (paramNode.getParamType() == ParamType.OBJECT) {
            if (paramNode.getChildren() != null && !paramNode.getChildren().isEmpty()) {
                hasRecords = true;
                // 替换单个对象的占位符
                String recordValues = valueTemplate;
                for (ParamTreeNode field : paramNode.getChildren()) {
                    String formattedValue = formatValue(field.getParamValue());
                    recordValues = recordValues.replace("{" + field.getParamKey() + "}", formattedValue);
                }
                valuesBuilder.append(recordValues);
            }
        } else {
            throw new AgentConfigException(AgentErrorCode.SQL_UNSUPPORTED_PARAM_TYPE, String.valueOf(paramNode.getParamType()));
        }

        String result = hasRecords ? prefix + " " + valuesBuilder : "";

        // 如果存在ON DUPLICATE KEY UPDATE部分，直接附加到结果后面
        if (!updatePart.isEmpty()) {
            result += " " + updatePart;
        }

        // 检查是否还有未替换的占位符
        if (result.contains("{") && result.contains("}")) {
            throw new AgentConfigException(AgentErrorCode.SQL_PLACEHOLDER_UNMATCHED, "SQL语句中存在未匹配的参数占位符: " + result);
        }

        return result;
    }

    /**
     * 构建SQL查询语句
     */
    private String buildSelectSql(String sqlTemplate, List<ParamTreeNode> params) {
        String finalSql = sqlTemplate;

        for (ParamTreeNode param : params) {
            String formattedValue;
            // 处理数组类型参数，用于IN语句
            if (param.getParamType() == ParamType.ARRAY || param.getParamType() == ParamType.PURE_ARRAY) {
                formattedValue = processArrayNode(param);
            } else {
                // 处理普通类型参数
                formattedValue = formatValue(param.getParamValue());
            }
            finalSql = finalSql.replace(DatabaseSqlConstants.PLACEHOLDER_START +
                    param.getParamKey() + DatabaseSqlConstants.PLACEHOLDER_END, formattedValue);
        }

        // 检查是否还有未替换的占位符
        if (finalSql.contains(DatabaseSqlConstants.PLACEHOLDER_START) && finalSql.contains(DatabaseSqlConstants.PLACEHOLDER_END)) {
            throw new AgentConfigException(AgentErrorCode.SQL_PLACEHOLDER_UNMATCHED, "SQL语句中存在未匹配的参数占位符: " + finalSql);
        }

        return finalSql;
    }

    private String processArrayNode(ParamTreeNode node) {
        JSONArray jsonArray = new JSONArray();

        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            // 遍历所有虚拟节点
            for (ParamTreeNode virtualNode : node.getChildren()) {
                // 虚拟节点的子节点才是真正的元素
                if (virtualNode.getChildren() != null && !virtualNode.getChildren().isEmpty()) {
                    List<ParamTreeNode> actualNodes = virtualNode.getChildren();
                    // 对于查询语句的数组场景，只处理单元素节点
                    if (actualNodes.size() > 1) {
                        throw new AgentConfigException(AgentErrorCode.SQL_ARRAY_CHILD_SIZE_ERROR);
                    }
                    Object value = actualNodes.get(0).getParamValue();
                    // 根据配置项中是否保留单元素节点的key
                    jsonArray.add(value);
                }
            }
        }
        return formatArrayValue(JsonUtils.toJsonString(jsonArray));
    }

    /**
     * 根据值的类型格式化SQL中的值
     */
    private String formatValue(Object value) {
        if (value == null) {
            return DatabaseSqlConstants.SQL_NULL;
        }
        if (value instanceof String || value instanceof Character) {
            // 转义单引号,防止SQL注入
            return "'" + value.toString().replace("'", "''") + "'";
        }
        // 数字类型直接返回字符串表示
        return value.toString();
    }

    /**
     * 数组格式处理,只会在in语句中出现
     * 将数组格式 ["117","102","94","87"] 转换为 SQL 的 ('117','102','94','87') 格式
     */
    private String formatArrayValue(Object value) {
        if (value == null) {
            return DatabaseSqlConstants.SQL_NULL;
        }

        // 直接将字符串形式的数组转换为SQL格式
        String stringValue = value.toString();

        // 移除方括号并添加圆括号,将双引号转换为单引号
        return stringValue
                .replace("[", "(")
                .replace("]", ")")
                .replace("\"", "'");
    }

    /**
     * 执行SQL并返回原始JSON响应
     */
    public String executeSqlAndGetRawResponse(WorkflowNode flowNode, String sql) {
        Map<String, Object> result = invokeSqlAndConvertResult(flowNode, sql);
        return JsonUtils.toJsonStringWithNulls(result);
    }

    public Map<String, Object> invokeSqlAndConvertResult(WorkflowNode flowNode, String sql) {
        Connection connection;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Map<String, Object> metaInfo = JsonUtils.parseMetaInfo(flowNode.getMetaInfo());
            connection = clientManager.getConnection(metaInfo);
        } catch (SQLException e) {
            throw new AgentInvocationException(AgentErrorCode.DB_CONNECTION_FAILED, e.getMessage(), e);
        }

        try {
            ps = connection.prepareStatement(sql);
            ps.setQueryTimeout(DatabaseApiClientManager.DEFAULT_QUERY_TIMEOUT_SECONDS);
            String sqlType = sql.trim().toUpperCase();
            if (sqlType.startsWith(DatabaseSqlConstants.SQL_KEYWORD_SELECT)) {
                rs = ps.executeQuery();
                return convertResultSetToMap(rs);
            } else {
                int affectedRows = ps.executeUpdate();
                Map<String, Object> result = new HashMap<>();
                result.put(DatabaseSqlConstants.RESPONSE_FIELD_AFFECTED_ROWS, affectedRows);
                return result;
            }
        } catch (SQLException e) {
            throw new AgentInvocationException(AgentErrorCode.DB_EXECUTION_FAILED, "SQL: " + sql, e);
        } finally {
            closeQuietly(rs, ps, connection);
        }
    }

    /**
     * 将结果集转换为Map
     */
    public Map<String, Object> convertResultSetToMap(ResultSet rs) throws SQLException {

        // 用于存储所有行数据
        List<Map<String, Object>> dataList = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (rs.next()) {
            // 存储单行数据的Map
            Map<String, Object> rowMap = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                Object value = rs.getObject(i);
                rowMap.put(columnName, value);
            }
            dataList.add(rowMap);
        }

        // 构建最终要返回的Map,包含数据列表和总行数
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(DatabaseSqlConstants.RESPONSE_FIELD_DATA, dataList);
        resultMap.put(DatabaseSqlConstants.RESPONSE_FIELD_TOTAL, dataList.size());
        return resultMap;
    }

    private void closeQuietly(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
