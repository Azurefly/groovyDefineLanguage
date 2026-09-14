package com.pl.gdl.dataframe.operator.advanced;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CommandMethod {
    String value() default "";
}
