package io.micronaut.python.compiler;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RepeatableAnnotations.class)
public @interface RepeatableAnnotation {
    String value() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@interface RepeatableAnnotations {
    RepeatableAnnotation[] value();
}
