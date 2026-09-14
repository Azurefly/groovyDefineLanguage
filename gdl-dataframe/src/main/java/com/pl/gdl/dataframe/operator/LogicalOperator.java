package com.pl.gdl.dataframe.operator;

import com.pl.gdl.common.constant.GdlConstants;
import com.pl.gdl.common.model.ColumnInfo;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class LogicalOperator implements Serializable {
    protected final String operatorId;
    protected String nodeId;
    protected String alias;
    protected String areaCode = GdlConstants.DEFAULT_AREA_CODE;
    protected final String tempTableName;
    protected final List<LogicalOperator> upstream = new ArrayList<>();
    protected final List<LogicalOperator> dependencies = new ArrayList<>();
    protected List<ColumnInfo> outputSchema = new ArrayList<>();

    public LogicalOperator() {
        this.operatorId = UUID.randomUUID().toString().replace("-", "");
        this.tempTableName = GdlConstants.TEMP_TABLE_PREFIX + this.operatorId.substring(0, 16);
    }

    public String getOperatorId() { return operatorId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }

    public String getTempTableName() { return tempTableName; }

    public List<LogicalOperator> getUpstream() { return upstream; }
    public void addUpstream(LogicalOperator op) {
        if (op != null) {
            this.upstream.add(op);
            if (this.areaCode.equals(GdlConstants.DEFAULT_AREA_CODE) && !op.getAreaCode().equals(GdlConstants.DEFAULT_AREA_CODE)) {
                this.areaCode = op.getAreaCode();
            }
        }
    }

    public List<LogicalOperator> getDependencies() { return dependencies; }
    public void addDependency(LogicalOperator op) {
        if (op != null) {
            this.dependencies.add(op);
        }
    }

    public List<ColumnInfo> getOutputSchema() { return outputSchema; }
    public void setOutputSchema(List<ColumnInfo> schema) {
        this.outputSchema = schema != null ? schema : new ArrayList<>();
    }

    public abstract String getOperatorName();

    @Override
    public String toString() {
        return getOperatorName() + "{" +
                "nodeId='" + nodeId + '\'' +
                ", alias='" + alias + '\'' +
                ", areaCode='" + areaCode + '\'' +
                '}';
    }
}
