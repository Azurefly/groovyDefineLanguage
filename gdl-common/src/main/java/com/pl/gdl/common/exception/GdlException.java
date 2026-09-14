package com.pl.gdl.common.exception;

public class GdlException extends RuntimeException {
    public GdlException(String message) {
        super(message);
    }

    public GdlException(String message, Throwable cause) {
        super(message, cause);
    }
}
