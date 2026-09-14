package com.pl.gdl.ontology.annotation;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Column {
    String cName() default "";
    String dataTypeName() default "string";
    String remarks() default "";
    int dataLength() default 255;
    int decimalDigits() default 0;
    boolean nullable() default true;
    boolean partition() default false;
    boolean primaryKey() default false;
    String originalColumnName() default "";
    String partitionRule() default "";
}
