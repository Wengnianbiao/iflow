package com.helianhealth.agent.mysql.util;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface ResultSetHandler<R> {
    R apply(ResultSet resultSet) throws SQLException;
}
