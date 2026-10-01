package io.micronaut.reflection

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME)
@interface Documented1 {
    String value() default "x"
}
