package com.pl.gdl.dataframe.operator;

import com.pl.gdl.common.constant.GdlConstants;
import com.pl.gdl.common.model.ColumnInfo;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class LogicalOperator implements Serializable {
    /**
     * 当前线程的算子监听器栈。栈顶监听器会收到本线程内算子构建/变更事件。
     */
    private static final ThreadLocal<Deque<Consumer<LogicalOperator>>> OPERATOR_LISTENERS =
            ThreadLocal.withInitial(ArrayDeque::new);

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

        notifyListener(this);
    }

    /**
     * 将监听器压入当前线程的监听器栈顶。
     * <p>
     * 栈式管理支持嵌套使用：谁 push 谁负责 pop，建议配合 try-finally 使用，
     * 以免监听器泄漏，影响同线程后续算子构建。
     */
    public static void pushListener(Consumer<LogicalOperator> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        OPERATOR_LISTENERS.get().push(listener);
    }

    /**
     * 弹出当前线程监听器栈顶的监听器；栈为空时无操作。
     */
    public static void popListener() {
        Deque<Consumer<LogicalOperator>> stack = OPERATOR_LISTENERS.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /**
     * 设置全局监听器（保留兼容语义）。
     * <p>
     * 注意这是 ThreadLocal 语义：只影响当前线程。传入非空监听器时替换当前线程的
     * 整个监听器栈（清空栈后压入该监听器）；传入 null 时清空当前线程的整个监听器栈。
     */
    public static void setGlobalListener(Consumer<LogicalOperator> listener) {
        Deque<Consumer<LogicalOperator>> stack = OPERATOR_LISTENERS.get();
        stack.clear();
        if (listener != null) {
            stack.push(listener);
        }
    }

    /**
     * 通知当前线程监听器栈顶的监听器；栈为空时无操作。
     */
    private static void notifyListener(LogicalOperator op) {
        Deque<Consumer<LogicalOperator>> stack = OPERATOR_LISTENERS.get();
        if (!stack.isEmpty()) {
            stack.peek().accept(op);
        }
    }

    public String getOperatorId() { return operatorId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
        notifyListener(this);
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
            notifyListener(this);
        }
    }

    public List<LogicalOperator> getDependencies() { return dependencies; }
    public void addDependency(LogicalOperator op) {
        if (op != null) {
            this.dependencies.add(op);
            notifyListener(this);
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
