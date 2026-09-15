package com.pl.gdl.dataframe.engine;

import com.pl.gdl.common.exception.GdlExecutionException;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.JdbcDatasource;
import com.pl.gdl.dataframe.dialect.SqlDialect;
import com.pl.gdl.dataframe.operator.LogicalOperator;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Executes generated/passthrough SQL against any JdbcDatasource. */
public class JdbcExecutionEngine extends SqlPushdownEngine {
    private final JdbcDatasource datasource;

    public JdbcExecutionEngine(JdbcDatasource datasource, SqlDialect dialect) {
        super(Objects.requireNonNull(dialect, "dialect must not be null"));
        this.datasource = Objects.requireNonNull(datasource, "datasource must not be null");
    }

    public JdbcDatasource getDatasource() {
        return datasource;
    }

    @Override
    public RowDataFrame execute(LogicalOperator operator) {
        String sql = toSql(operator);
        if (sql == null || sql.isBlank()) return new RowDataFrame();
        loadDriver();

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            boolean hasResultSet = statement.execute(sql);
            if (!hasResultSet) return new RowDataFrame();
            try (ResultSet resultSet = statement.getResultSet()) {
                return read(resultSet);
            }
        } catch (SQLException e) {
            throw new GdlExecutionException("JDBC execution failed for " + getDialect().getDialectName()
                    + ": " + e.getMessage(), e);
        }
    }

    private Connection openConnection() throws SQLException {
        String username = datasource.getUsername();
        if (username == null) return DriverManager.getConnection(datasource.getJdbcUrl());
        return DriverManager.getConnection(datasource.getJdbcUrl(), username,
                datasource.getPassword() == null ? "" : datasource.getPassword());
    }

    private void loadDriver() {
        String driver = datasource.getDriverClassName();
        if (driver == null || driver.isBlank()) return;
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new GdlExecutionException("JDBC driver not found: " + driver, e);
        }
    }

    private RowDataFrame read(ResultSet rs) throws SQLException {
        RowDataFrame frame = new RowDataFrame();
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= count; i++) {
                String label = meta.getColumnLabel(i);
                if (label == null || label.isBlank()) label = meta.getColumnName(i);
                row.put(label, rs.getObject(i));
            }
            frame.addRowValue(row);
        }
        return frame;
    }
}
