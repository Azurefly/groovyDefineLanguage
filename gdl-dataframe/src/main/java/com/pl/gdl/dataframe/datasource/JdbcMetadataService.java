package com.pl.gdl.dataframe.datasource;

import com.pl.gdl.dataframe.engine.JdbcConnectionManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Uniform JDBC metadata and health inspection for datasource tools/UIs. */
public class JdbcMetadataService {
    private final JdbcConnectionManager connectionManager;

    public JdbcMetadataService() {
        this(JdbcConnectionManager.getDefault());
    }

    public JdbcMetadataService(JdbcConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public DatasourceHealth health(JdbcDatasource datasource) {
        long start = System.nanoTime();
        try (Connection connection = connectionManager.getConnection(datasource)) {
            DatabaseMetaData meta = connection.getMetaData();
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            return DatasourceHealth.healthy(latencyMs, meta.getDatabaseProductName(), meta.getDatabaseProductVersion());
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            return DatasourceHealth.unhealthy(latencyMs, e.getMessage());
        }
    }

    public Snapshot inspect(JdbcDatasource datasource) {
        try (Connection connection = connectionManager.getConnection(datasource)) {
            DatabaseMetaData meta = connection.getMetaData();
            List<TableInfo> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String catalog = rs.getString("TABLE_CAT");
                    String schema = rs.getString("TABLE_SCHEM");
                    String table = rs.getString("TABLE_NAME");
                    String type = rs.getString("TABLE_TYPE");
                    tables.add(new TableInfo(catalog, schema, table, type, columns(meta, catalog, schema, table)));
                }
            }
            return new Snapshot(meta.getDatabaseProductName(), meta.getDatabaseProductVersion(), tables);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to inspect JDBC metadata: " + e.getMessage(), e);
        }
    }

    private List<ColumnInfo> columns(DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, table, "%")) {
            while (rs.next()) {
                columns.add(new ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("DATA_TYPE"),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        rs.getInt("ORDINAL_POSITION")));
            }
        }
        return columns;
    }

    public record Snapshot(String productName, String productVersion, List<TableInfo> tables) {}
    public record TableInfo(String catalog, String schema, String name, String type, List<ColumnInfo> columns) {}
    public record ColumnInfo(String name, String typeName, int jdbcType, int size, boolean nullable, int ordinalPosition) {}
}
