package com.helianhealth.agent.common.typehandler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * MyBatis TypeHandler for List<Integer>
 * 用于将 List<Integer> 与数据库 VARCHAR/TEXT 字段之间进行 JSON 序列化/反序列化
 */
public class ListIntegerTypeHandler extends BaseTypeHandler<List<Integer>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Integer> parameter, JdbcType jdbcType) throws SQLException {
        try {
            // 将 List<Integer> 序列化为 JSON 字符串存入数据库
            String json = JSON.toJSONString(parameter);
            ps.setString(i, json);
        } catch (Exception e) {
            throw new SQLException("Failed to serialize List<Integer> to JSON", e);
        }
    }

    @Override
    public List<Integer> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return jsonToIntegerList(json);
    }

    @Override
    public List<Integer> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return jsonToIntegerList(json);
    }

    @Override
    public List<Integer> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return jsonToIntegerList(json);
    }

    private List<Integer> jsonToIntegerList(String json) throws SQLException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            // 将 JSON 字符串反序列化为 List<Integer>
            return JSON.parseObject(json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            throw new SQLException("Failed to deserialize JSON to List<Integer>", e);
        }
    }
}
