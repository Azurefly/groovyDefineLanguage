package com.pl.gdl.common.exception;

/** 生命周期钩子否决：before* 钩子返回 false 时中断写操作 */
public class HookVetoException extends GdlExecutionException {
    public HookVetoException(String message) {
        super(message);
    }
}
