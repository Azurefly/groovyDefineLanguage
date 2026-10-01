package com.pl.gdl.common.exception;

/**
 * GDL 引擎异常基类：所有 GDL 相关受检异常语义均封装为此类运行时异常。
 */
public class GdlException extends RuntimeException {
    public GdlException(String message) {
        super(message);
    }

    public GdlException(String message, Throwable cause) {
        super(message, cause);
    }
}
