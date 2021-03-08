package io.micronaut.docs.qualifiers.annotationmember;


import io.micronaut.context.annotation.NonBinding;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Retention(RUNTIME)
public @interface Cylinders {
    int value();

    @NonBinding
    String description() default "";
}
