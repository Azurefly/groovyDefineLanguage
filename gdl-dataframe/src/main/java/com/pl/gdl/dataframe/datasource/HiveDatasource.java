package com.pl.gdl.dataframe.datasource;

public class HiveDatasource extends CmdDatasource {
    public HiveDatasource() {
        super("default");
    }

    public HiveDatasource(String dsConfName) {
        super(dsConfName);
    }

    @Override
    public String getDatasourceType() {
        return "HIVE";
    }

    @Override
    public String toString() {
        return "HiveDatasource{conf='" + dsConfName + "', areaCode='" + areaCode + "'}";
    }
}
