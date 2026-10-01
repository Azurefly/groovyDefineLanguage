package com.pl.gdl.common.exception;

/**
 * GDL 编译期异常：脚本解析、语义分析或代码生成阶段发生的错误。
 */
public class GdlCompilationException extends GdlException {
    public GdlCompilationException(String message) {
        super(message);
    }

    public GdlCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
