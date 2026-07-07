package com.helianhealth.agent.mysql.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.mysql.util.ExecuteFunction;
import com.helianhealth.agent.mysql.util.Query;
import com.helianhealth.agent.mysql.util.QueryFunction;
import com.helianhealth.agent.mysql.util.TransactionalFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * MySQL JDBC
 */
public abstract class MySqlBaseDAO {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper;
    protected final DataSource dataSource;

    protected MySqlBaseDAO(ObjectMapper objectMapper, DataSource dataSource) {
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
    }

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    protected <T> T readValue(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    protected <R> R getWithTransaction(TransactionalFunction<R> function) {
        try (Connection tx = dataSource.getConnection()) {
            boolean previousAutoCommit = tx.getAutoCommit();
            tx.setAutoCommit(false);
            try {
                R result = function.apply(tx);
                tx.commit();
                return result;
            } catch (Throwable th) {
                tx.rollback();
                if (th instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(th.getMessage(), th);
            } finally {
                tx.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    protected void withTransaction(Consumer<Connection> consumer) {
        getWithTransaction(
                connection -> {
                    consumer.accept(connection);
                    return null;
                });
    }

    protected <R> R queryWithTransaction(String sql, QueryFunction<R> function) {
        return getWithTransaction(tx -> query(tx, sql, function));
    }

    protected <R> R query(Connection tx, String sql, QueryFunction<R> function) {
        try (Query q = new Query(objectMapper, tx, sql)) {
            return function.apply(q);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    protected void execute(Connection tx, String sql, ExecuteFunction function) {
        try (Query q = new Query(objectMapper, tx, sql)) {
            function.apply(q);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    protected void executeWithTransaction(String sql, ExecuteFunction function) {
        withTransaction(tx -> execute(tx, sql, function));
    }
}
