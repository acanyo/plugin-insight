package com.xhhao.insight.storage.database;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlWork<T> {

    T execute(Connection connection) throws SQLException;
}
