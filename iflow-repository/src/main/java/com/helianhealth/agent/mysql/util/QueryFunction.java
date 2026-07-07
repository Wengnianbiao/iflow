package com.helianhealth.agent.mysql.util;

import java.sql.SQLException;

@FunctionalInterface
public interface QueryFunction<R> {
    R apply(Query query) throws SQLException;
}
