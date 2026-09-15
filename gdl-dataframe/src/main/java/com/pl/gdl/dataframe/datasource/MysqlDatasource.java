package com.pl.gdl.dataframe.datasource;

public class MysqlDatasource extends CmdDatasource implements JdbcDatasource {
    private String host;
    private int port = 3306;
    private String database;
    private String username;
    private String password;
    private String parameters = "useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";

    public MysqlDatasource() {
        super("mysql-default");
    }

    public MysqlDatasource(String host, int port, String database, String username, String password) {
        super(database);
        this.host = host;
        this.port = port > 0 ? port : 3306;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    @Override public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    @Override public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }

    @Override public String getDatasourceType() { return "MYSQL"; }
    @Override public String getDriverClassName() { return "com.mysql.cj.jdbc.Driver"; }

    @Override
    public String getJdbcUrl() {
        String base = "jdbc:mysql://" + host + ":" + port + "/" + database;
        return parameters == null || parameters.isBlank() ? base : base + "?" + parameters;
    }

    @Override
    public String toString() {
        return "MysqlDatasource{" + host + ":" + port + "/" + database + ", user='" + username + "', areaCode='" + areaCode + "'}";
    }
}
