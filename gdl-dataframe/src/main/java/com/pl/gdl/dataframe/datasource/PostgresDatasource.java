package com.pl.gdl.dataframe.datasource;

public class PostgresDatasource extends CmdDatasource implements JdbcDatasource {
    private String host;
    private int port = 5432;
    private String database;
    private String username;
    private String password;

    public PostgresDatasource() {
        super("postgres-default");
    }

    public PostgresDatasource(String host, int port, String database, String username, String password) {
        super(database);
        this.host = host;
        this.port = port > 0 ? port : 5432;
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

    @Override public String getDatasourceType() { return "POSTGRES"; }
    @Override public String getDriverClassName() { return "org.postgresql.Driver"; }

    @Override
    public String getJdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    @Override
    public String toString() {
        return "PostgresDatasource{" + host + ":" + port + "/" + database + ", user='" + username + "', areaCode='" + areaCode + "'}";
    }
}
