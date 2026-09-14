package com.pl.gdl.ontology.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Table {
    String type() default "ORC";
    String remarks() default "";
    boolean readOnly() default true;
    KeyValue[] properties() default {};
}
