package io.micronaut.inject.annotation.repeatable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Topics {
    Topic[] value();
}
