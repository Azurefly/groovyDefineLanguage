package com.pl.gdl.dataframe.datasource;

import com.pl.gdl.common.constant.GdlConstants;
import java.io.Serializable;

public abstract class CmdDatasource implements Serializable {
    protected String areaCode = GdlConstants.DEFAULT_AREA_CODE;
    protected String dsConfName = "default";

    public CmdDatasource() {}

    public CmdDatasource(String dsConfName) {
        this.dsConfName = dsConfName != null ? dsConfName : "default";
    }

    public CmdDatasource areaCode(String areaCode) {
        this.areaCode = areaCode != null ? areaCode : GdlConstants.DEFAULT_AREA_CODE;
        return this;
    }

    public String getAreaCode() {
        return areaCode;
    }

    public void setAreaCode(String areaCode) {
        this.areaCode = areaCode;
    }

    public String getDsConfName() {
        return dsConfName;
    }

    public void setDsConfName(String dsConfName) {
        this.dsConfName = dsConfName;
    }

    public boolean isRemote(String localAreaCode) {
        if (areaCode == null || areaCode.equalsIgnoreCase(GdlConstants.DEFAULT_AREA_CODE)) {
            return false;
        }
        return localAreaCode != null && !areaCode.equalsIgnoreCase(localAreaCode);
    }

    public abstract String getDatasourceType();
}
