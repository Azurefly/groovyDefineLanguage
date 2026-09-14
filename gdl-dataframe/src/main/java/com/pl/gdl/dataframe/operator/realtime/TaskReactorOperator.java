package com.pl.gdl.dataframe.operator.realtime;

import com.pl.gdl.dataframe.operator.LogicalOperator;
import java.util.ArrayList;
import java.util.List;

public class TaskReactorOperator extends LogicalOperator {
    private final List<String> taskIds;
    private final int successPercent;
    private final int delaySeconds;

    public TaskReactorOperator(List<String> taskIds, int successPercent, int delaySeconds) {
        this.taskIds = taskIds != null ? new ArrayList<>(taskIds) : new ArrayList<>();
        this.successPercent = successPercent;
        this.delaySeconds = delaySeconds;
    }

    public List<String> getTaskIds() { return taskIds; }
    public int getSuccessPercent() { return successPercent; }
    public int getDelaySeconds() { return delaySeconds; }

    @Override
    public String getOperatorName() {
        return "taskReactor";
    }

    @Override
    public String toString() {
        return "taskReactor(" + taskIds + ", " + successPercent + "%, " + delaySeconds + "s)";
    }
}
