package com.pl.gdl.common.model;

import java.io.Serializable;

public class RegisterRsp implements Serializable {
    public static final int STATUS_FAILED = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_PARTIAL = 2;

    private int status;
    private String message;
    private Object data;

    public RegisterRsp() {}

    public RegisterRsp(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public RegisterRsp(int status, String message, Object data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static RegisterRsp success(String message) {
        return new RegisterRsp(STATUS_SUCCESS, message);
    }

    public static RegisterRsp success(String message, Object data) {
        return new RegisterRsp(STATUS_SUCCESS, message, data);
    }

    public static RegisterRsp fail(String message) {
        return new RegisterRsp(STATUS_FAILED, message);
    }

    public static RegisterRsp partial(String message, Object data) {
        return new RegisterRsp(STATUS_PARTIAL, message, data);
    }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    @Override
    public String toString() {
        return "RegisterRsp{status=" + status + ", message='" + message + '\'' + ", data=" + data + '}';
    }
}
