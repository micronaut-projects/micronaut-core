package io.micronaut.inject.annotation.repeatable;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Topics.class)
public @interface Topic {
    String value();

    int qos() default 1;
}
