package com.helianhealth.agent.mysql.util;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface TransactionalFunction<R> {
    R apply(Connection tx) throws SQLException;
}
