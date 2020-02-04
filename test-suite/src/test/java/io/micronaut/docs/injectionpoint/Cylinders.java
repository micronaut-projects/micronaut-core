package io.micronaut.docs.injectionpoint;

import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

// tag::class[]
@Documented
@Retention(RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Cylinders {
    int value() default 8;
}
// end::class[]
