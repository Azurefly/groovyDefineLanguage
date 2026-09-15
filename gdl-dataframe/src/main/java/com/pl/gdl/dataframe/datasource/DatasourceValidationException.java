package com.pl.gdl.dataframe.datasource;

/** Raised when a provider rejects datasource configuration before construction. */
public class DatasourceValidationException extends IllegalArgumentException {
    public DatasourceValidationException(String message) {
        super(message);
    }
}
