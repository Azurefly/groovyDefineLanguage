package com.pl.gdl.common.exception;

/** 乐观锁冲突：更新时 version 不匹配，数据已被其他事务修改 */
public class OptimisticLockException extends GdlExecutionException {
    public OptimisticLockException(String message) {
        super(message);
    }
}
