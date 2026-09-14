package com.pl.gdl.ontology.annotation;

import java.lang.annotation.*;

@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KeyValue {
    String key();
    String value();
}
