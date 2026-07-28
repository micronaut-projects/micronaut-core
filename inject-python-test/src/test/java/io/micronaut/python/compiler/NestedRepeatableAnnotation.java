package io.micronaut.python.compiler;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(NestedRepeatableAnnotation.List.class)
public @interface NestedRepeatableAnnotation {
    String value() default "";

    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        NestedRepeatableAnnotation[] value();
    }
}
