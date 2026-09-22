package com.helianhealth.iflow.web.mysql.util;

import java.sql.SQLException;

@FunctionalInterface
public interface QueryFunction<R> {
    R apply(Query query) throws SQLException;
}
