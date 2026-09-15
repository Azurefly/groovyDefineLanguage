package com.pl.gdl.dataframe.engine;

import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.Row;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dialect.H2SqlDialect;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.advanced.GroovyCustomOperator;
import com.pl.gdl.dataframe.operator.base.FromOperator;
import org.h2.jdbcx.JdbcDataSource;

import java.sql.*;
import java.util.*;

public class InMemoryEngine implements ExecutionEngine {
    private final JdbcDataSource dataSource;
    private final SqlPushdownEngine sqlEngine;
    private final Map<String, RowDataFrame> inMemoryTables = new LinkedHashMap<>();

    public InMemoryEngine() {
        this.dataSource = new JdbcDataSource();
        this.dataSource.setURL("jdbc:h2:mem:gdl_test_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1;MODE=LEGACY");
        this.sqlEngine = new SqlPushdownEngine(new H2SqlDialect());
    }

    public void registerTable(String tableName, RowDataFrame df) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        Objects.requireNonNull(df, "df must not be null");

        String normalizedTableName = tableName.trim();
        createAndPopulateH2Table(normalizedTableName, df);
        inMemoryTables.put(normalizedTableName.toLowerCase(Locale.ROOT), df);
    }

    private void createAndPopulateH2Table(String tableName, RowDataFrame df) {
        if (df.getColumns().isEmpty()) return;
        try (Connection conn = dataSource.getConnection()) {
            StringBuilder sb = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
            for (int i = 0; i < df.getColumns().size(); i++) {
                if (i > 0) sb.append(", ");
                ColumnInfo col = df.getColumns().get(i);
                sb.append(col.getColumnName()).append(" VARCHAR(500)");
            }
            sb.append(")");
            try (Statement stmt = conn.createStatement()) {
                // registerTable has replacement semantics: keep the direct in-memory view
                // and the SQL-backed view consistent, including when the schema changes.
                stmt.execute("DROP TABLE IF EXISTS " + tableName);
                stmt.execute(sb.toString());
            }

            if (df.rowSize() > 0) {
                StringBuilder ins = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES (");
                for (int i = 0; i < df.getColumns().size(); i++) {
                    if (i > 0) ins.append(", ");
                    ins.append("?");
                }
                ins.append(")");
                try (PreparedStatement ps = conn.prepareStatement(ins.toString())) {
                    for (Row row : df) {
                        for (int i = 0; i < df.getColumns().size(); i++) {
                            Object val = row.getValue(df.getColumns().get(i).getColumnName());
                            ps.setObject(i + 1, val != null ? String.valueOf(val) : null);
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register H2 table: " + tableName, e);
        }
    }

    @Override
    public RowDataFrame execute(LogicalOperator operator) {
        if (operator instanceof GroovyCustomOperator groovyOp) {
            RowDataFrame input = !groovyOp.getUpstream().isEmpty() ? execute(groovyOp.getUpstream().get(0)) : new RowDataFrame();
            if (groovyOp.getClosure() != null) {
                return groovyOp.getClosure().call(input);
            }
            return input;
        }

        if (operator instanceof FromOperator fromOp) {
            String tbl = fromOp.getTableName().toLowerCase(Locale.ROOT);
            if (inMemoryTables.containsKey(tbl)) {
                return inMemoryTables.get(tbl);
            }
        }

        String sql = toSql(operator);
        if (sql == null || sql.isBlank()) {
            return new RowDataFrame();
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            boolean isQuery = sql.trim().toUpperCase(Locale.ROOT).startsWith("SELECT") ||
                    sql.trim().toUpperCase(Locale.ROOT).startsWith("WITH");
            if (isQuery) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int cols = md.getColumnCount();
                    List<ColumnInfo> columns = new ArrayList<>();
                    for (int i = 1; i <= cols; i++) {
                        ColumnInfo ci = new ColumnInfo(md.getColumnLabel(i), md.getColumnTypeName(i));
                        columns.add(ci);
                    }
                    RowDataFrame result = new RowDataFrame(columns);
                    while (rs.next()) {
                        Row row = new Row();
                        for (int i = 1; i <= cols; i++) {
                            row.setValue(md.getColumnLabel(i), rs.getObject(i));
                        }
                        result.addRow(row);
                    }
                    return result;
                }
            } else {
                stmt.execute(sql);
                return new RowDataFrame();
            }
        } catch (SQLException e) {
            // Return empty DataFrame on mock query if table not present
            return new RowDataFrame();
        }
    }

    @Override
    public String toSql(LogicalOperator operator) {
        return sqlEngine.toSql(operator);
    }
}
