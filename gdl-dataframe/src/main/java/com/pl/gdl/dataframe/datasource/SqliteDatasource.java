package com.pl.gdl.dataframe.datasource;

public class SqliteDatasource extends CmdDatasource implements JdbcDatasource {
    private String path;

    public SqliteDatasource() {
        this(":memory:");
    }

    public SqliteDatasource(String path) {
        super(path == null || path.isBlank() ? "sqlite-memory" : path);
        this.path = path == null || path.isBlank() ? ":memory:" : path;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path == null || path.isBlank() ? ":memory:" : path; }

    @Override public String getDatasourceType() { return "SQLITE"; }
    @Override public String getJdbcUrl() { return "jdbc:sqlite:" + path; }
    @Override public String getUsername() { return null; }
    @Override public String getPassword() { return null; }
    @Override public String getDriverClassName() { return "org.sqlite.JDBC"; }

    @Override
    public String toString() {
        return "SqliteDatasource{path='" + path + "', areaCode='" + areaCode + "'}";
    }
}
