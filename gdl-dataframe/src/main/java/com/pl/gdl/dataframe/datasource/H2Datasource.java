package com.pl.gdl.dataframe.datasource;

public class H2Datasource extends CmdDatasource implements JdbcDatasource {
    private String jdbcUrl;
    private String username = "sa";
    private String password = "";

    public H2Datasource() {
        this("jdbc:h2:mem:gdl;DB_CLOSE_DELAY=-1", "sa", "");
    }

    public H2Datasource(String jdbcUrl, String username, String password) {
        super("h2");
        this.jdbcUrl = jdbcUrl == null || jdbcUrl.isBlank() ? "jdbc:h2:mem:gdl;DB_CLOSE_DELAY=-1" : jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override public String getDatasourceType() { return "H2"; }
    @Override public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    @Override public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    @Override public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    @Override public String getDriverClassName() { return "org.h2.Driver"; }
}
