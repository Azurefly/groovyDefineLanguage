package com.pl.gdl.common.model;

import com.pl.gdl.common.enums.TaskStatus;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskResult implements Serializable {
    private String status = TaskStatus.FINISHED.name();
    private String taskId;
    private String message = "Success";
    private List<AreaResult> result = new ArrayList<>();

    public TaskResult() {}

    public TaskResult(String taskId, String status) {
        this.taskId = taskId;
        this.status = status;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<AreaResult> getResult() { return result; }
    public void setResult(List<AreaResult> result) { this.result = result; }

    public void addAreaResult(AreaResult ar) {
        this.result.add(ar);
    }

    public static class AreaResult implements Serializable {
        private String areaCode;
        private String code = "TRE_2000";
        private String msg = "成功";
        private List<Map<String, Object>> data = new ArrayList<>();
        private List<ColumnInfo> metadata = new ArrayList<>();

        public AreaResult() {}

        public AreaResult(String areaCode, List<Map<String, Object>> data, List<ColumnInfo> metadata) {
            this.areaCode = areaCode;
            this.data = data != null ? data : new ArrayList<>();
            this.metadata = metadata != null ? metadata : new ArrayList<>();
        }

        public String getAreaCode() { return areaCode; }
        public void setAreaCode(String areaCode) { this.areaCode = areaCode; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getMsg() { return msg; }
        public void setMsg(String msg) { this.msg = msg; }

        public List<Map<String, Object>> getData() { return data; }
        public void setData(List<Map<String, Object>> data) { this.data = data; }

        public List<ColumnInfo> getMetadata() { return metadata; }
        public void setMetadata(List<ColumnInfo> metadata) { this.metadata = metadata; }
    }
}
