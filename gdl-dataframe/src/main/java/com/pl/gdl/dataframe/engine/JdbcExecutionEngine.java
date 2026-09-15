package com.pl.gdl.dataframe.engine;

import com.pl.gdl.common.exception.GdlExecutionException;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.JdbcDatasource;
import com.pl.gdl.dataframe.dialect.SqlDialect;
import com.pl.gdl.dataframe.operator.LogicalOperator;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

        try (Connection connection = openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                RowDataFrame result = executeStatements(connection, splitStatements(sql));
                connection.commit();
                connection.setAutoCommit(originalAutoCommit);
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new GdlExecutionException("JDBC execution failed for " + getDialect().getDialectName()
                    + ": " + e.getMessage(), e);
        } catch (GdlExecutionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GdlExecutionException("JDBC execution failed for " + getDialect().getDialectName()
                    + ": " + e.getMessage(), e);
        }
    }

    private RowDataFrame executeStatements(Connection connection, List<String> statements) throws SQLException {
        RowDataFrame result = new RowDataFrame();
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                if (sql.isBlank()) continue;
                boolean hasResultSet = statement.execute(sql);
                if (hasResultSet) {
                    try (ResultSet resultSet = statement.getResultSet()) {
                        result = read(resultSet);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Split dialect-generated multi-statement SQL without breaking semicolons
     * inside string literals or quoted identifiers.
     */
    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (quote != 0) {
                current.append(ch);
                if (ch == quote) {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                        current.append(sql.charAt(++i));
                    } else {
                        quote = 0;
                    }
                } else if (ch == '\\' && i + 1 < sql.length() && quote == '\'') {
                    current.append(sql.charAt(++i));
                }
                continue;
            }

            if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
                current.append(ch);
            } else if (ch == ';') {
                addStatement(statements, current);
            } else {
                current.append(ch);
            }
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String value = current.toString().trim();
        if (!value.isEmpty()) statements.add(value);
        current.setLength(0);
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
