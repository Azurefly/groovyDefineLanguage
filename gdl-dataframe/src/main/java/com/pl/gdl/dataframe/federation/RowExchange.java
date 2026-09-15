package com.pl.gdl.dataframe.federation;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.DatasourceIdentity;

/** Materialization boundary between physical datasource execution domains. */
public interface RowExchange {
    enum Mode { MEMORY, STREAMING, INTERMEDIATE_TABLE, REMOTE_DRIFT }

    String getName();
    Mode getMode();
    boolean supports(DatasourceIdentity source, DatasourceIdentity target, RowDataFrame rows);
    RowDataFrame transfer(DatasourceIdentity source, DatasourceIdentity target, RowDataFrame rows);
}
