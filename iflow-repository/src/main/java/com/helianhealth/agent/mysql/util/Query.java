package com.helianhealth.agent.mysql.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SQL 查询封装：按序绑定参数，并支持 JSON 列读写。
 */
public class Query implements AutoCloseable {

    private final ObjectMapper objectMapper;
    private final String rawQuery;
    private final AtomicInteger index = new AtomicInteger(1);
    private final PreparedStatement statement;

    public Query(ObjectMapper objectMapper, Connection connection, String query) {
        this.objectMapper = objectMapper;
        this.rawQuery = query;
        try {
            this.statement = connection.prepareStatement(query);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot prepare statement for query: " + query, ex);
        }
    }

    public Query addParameter(String value) {
        return addParameterInternal((ps, idx) -> ps.setString(idx, value));
    }

    public Query addParameter(int value) {
        return addParameterInternal((ps, idx) -> ps.setInt(idx, value));
    }

    public Query addParameter(long value) {
        return addParameterInternal((ps, idx) -> ps.setLong(idx, value));
    }

    public Query addParameter(Integer value) {
        if (value == null) {
            return addParameterInternal((ps, idx) -> ps.setObject(idx, null));
        }
        return addParameter(value.intValue());
    }

    public Query addParameter(Long value) {
        if (value == null) {
            return addParameterInternal((ps, idx) -> ps.setObject(idx, null));
        }
        return addParameter(value.longValue());
    }

    public Query addParameter(boolean value) {
        return addParameterInternal((ps, idx) -> ps.setBoolean(idx, value));
    }

    public Query addParameter(Boolean value) {
        if (value == null) {
            return addParameterInternal((ps, idx) -> ps.setObject(idx, null));
        }
        return addParameter(value.booleanValue());
    }

    public Query addJsonParameter(Object value) {
        return addParameter(toJson(value));
    }

    public boolean exists() {
        Object val = executeScalar();
        if (val == null) {
            return false;
        }
        if (val instanceof Number number) {
            return number.longValue() > 0;
        }
        if (val instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalStateException(
                "Expected Numeric/Boolean scalar from exists query, got " + val.getClass().getName());
    }

    public int executeUpdate() {
        try {
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("executeUpdate failed: " + rawQuery, ex);
        }
    }

    public boolean executeDelete() {
        return executeUpdate() > 0;
    }

    public Object executeScalar() {
        try (ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return rs.getObject(1);
        } catch (SQLException ex) {
            throw new IllegalStateException("executeScalar failed: " + rawQuery, ex);
        }
    }

    public <V> V executeAndFetchFirst(Class<V> returnType) {
        Object value = executeScalar();
        if (value == null) {
            return null;
        }
        return convert(value, returnType);
    }

    public <V> List<V> executeAndFetch(Class<V> returnType) {
        try (ResultSet rs = statement.executeQuery()) {
            List<V> list = new ArrayList<>();
            while (rs.next()) {
                list.add(convert(rs.getObject(1), returnType));
            }
            return list;
        } catch (SQLException ex) {
            throw new IllegalStateException("executeAndFetch failed: " + rawQuery, ex);
        }
    }

    public <V> V executeAndFetch(ResultSetHandler<V> handler) {
        try (ResultSet rs = statement.executeQuery()) {
            return handler.apply(rs);
        } catch (SQLException ex) {
            throw new IllegalStateException("executeAndFetch(handler) failed: " + rawQuery, ex);
        }
    }

    @Override
    public void close() {
        try {
            if (statement != null && !statement.isClosed()) {
                statement.close();
            }
        } catch (SQLException ignored) {
            // ignore close errors
        }
    }

    private Query addParameterInternal(InternalParameterSetter setter) {
        int idx = index.getAndIncrement();
        try {
            setter.apply(statement, idx);
            return this;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not bind parameter at index " + idx, ex);
        }
    }

    private <V> V convert(Object value, Class<V> returnType) {
        if (String.class == returnType) {
            return returnType.cast(value == null ? null : value.toString());
        }
        if (Integer.class == returnType) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return returnType.cast(number.intValue());
            }
            return returnType.cast(Integer.parseInt(value.toString()));
        }
        if (value instanceof String json) {
            return fromJson(json, returnType);
        }
        throw new IllegalStateException(
                "Cannot convert " + value.getClass().getName() + " to " + returnType.getName());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON serialize failed", ex);
        }
    }

    private <V> V fromJson(String value, Class<V> returnType) {
        try {
            return objectMapper.readValue(value, returnType);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not convert JSON to " + returnType.getName(), ex);
        }
    }

    @FunctionalInterface
    private interface InternalParameterSetter {
        void apply(PreparedStatement ps, int idx) throws SQLException;
    }
}
