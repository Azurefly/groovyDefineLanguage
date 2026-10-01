package com.pl.gdl.common.exception;

/**
 * 本体（Ontology）校验异常：注册本体时结构或字段校验不通过。
 *
 * <p>HTTP 服务层会将此类异常转换为 HTTP 400 响应，而非兜底 500。</p>
 */
public class OntologyValidationException extends GdlException {
    public OntologyValidationException(String message) {
        super(message);
    }

    public OntologyValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
