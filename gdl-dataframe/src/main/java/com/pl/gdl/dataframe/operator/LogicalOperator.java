package com.pl.gdl.dataframe.operator;

import com.pl.gdl.common.constant.GdlConstants;
import com.pl.gdl.common.model.ColumnInfo;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class LogicalOperator implements Serializable {
    private static final ThreadLocal<Consumer<LogicalOperator>> OPERATOR_LISTENER = new ThreadLocal<>();

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
        this.nodeId = "node_" + this.operatorId.substring(0, 8);

        Consumer<LogicalOperator> listener = OPERATOR_LISTENER.get();
        if (listener != null) {
            listener.accept(this);
        }
    }

    public static void setGlobalListener(Consumer<LogicalOperator> listener) {
        if (listener == null) {
            OPERATOR_LISTENER.remove();
        } else {
            OPERATOR_LISTENER.set(listener);
        }
    }

    public String getOperatorId() { return operatorId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
        Consumer<LogicalOperator> listener = OPERATOR_LISTENER.get();
        if (listener != null) {
            listener.accept(this);
        }
    }

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
            Consumer<LogicalOperator> listener = OPERATOR_LISTENER.get();
            if (listener != null) {
                listener.accept(this);
            }
        }
    }

    public List<LogicalOperator> getDependencies() { return dependencies; }
    public void addDependency(LogicalOperator op) {
        if (op != null) {
            this.dependencies.add(op);
            Consumer<LogicalOperator> listener = OPERATOR_LISTENER.get();
            if (listener != null) {
                listener.accept(this);
            }
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
