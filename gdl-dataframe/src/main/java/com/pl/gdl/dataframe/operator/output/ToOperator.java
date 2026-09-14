package com.pl.gdl.dataframe.operator.output;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.operator.LogicalOperator;

public class ToOperator extends LogicalOperator {
    private final CmdDatasource targetDatasource;
    private final String targetTableName;
    private String fieldsDdl;
    private long ttlSeconds = 0;
    private boolean overwrite = false;
    private boolean overwritePartition = false;
    private String partitionSpec;
    private boolean upsert = false;
    private boolean view = false;

    public ToOperator(LogicalOperator upstream, CmdDatasource targetDatasource, String targetTableName) {
        addUpstream(upstream);
        this.targetDatasource = targetDatasource;
        this.targetTableName = targetTableName;
        if (targetDatasource != null) {
            this.areaCode = targetDatasource.getAreaCode();
        }
    }

    public CmdDatasource getTargetDatasource() { return targetDatasource; }
    public String getTargetTableName() { return targetTableName; }

    public String getFieldsDdl() { return fieldsDdl; }
    public void setFieldsDdl(String fieldsDdl) { this.fieldsDdl = fieldsDdl; }

    public long getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }

    public boolean isOverwrite() { return overwrite; }
    public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; }

    public boolean isOverwritePartition() { return overwritePartition; }
    public void setOverwritePartition(boolean overwritePartition) { this.overwritePartition = overwritePartition; }

    public String getPartitionSpec() { return partitionSpec; }
    public void setPartitionSpec(String partitionSpec) { this.partitionSpec = partitionSpec; }

    public boolean isUpsert() { return upsert; }
    public void setUpsert(boolean upsert) { this.upsert = upsert; }

    public boolean isView() { return view; }
    public void setView(boolean view) { this.view = view; }

    @Override
    public String getOperatorName() {
        return "to";
    }

    @Override
    public String toString() {
        return "to(" + (targetDatasource != null ? targetDatasource.getDatasourceType() + ":" : "") + targetTableName + ")";
    }
}
