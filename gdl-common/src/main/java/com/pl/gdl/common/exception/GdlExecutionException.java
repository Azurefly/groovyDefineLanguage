package com.pl.gdl.common.exception;

/**
 * GDL 执行期异常：脚本/任务执行过程中发生的错误，如 SQL 执行失败、算子校验失败等。
 */
public class GdlExecutionException extends GdlException {
    public GdlExecutionException(String message) {
        super(message);
    }

    public GdlExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
