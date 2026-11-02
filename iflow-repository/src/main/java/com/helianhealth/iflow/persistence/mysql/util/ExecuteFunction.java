package com.helianhealth.iflow.persistence.mysql.util;

import java.sql.SQLException;

@FunctionalInterface
public interface ExecuteFunction {
    void apply(Query query) throws SQLException;
}
