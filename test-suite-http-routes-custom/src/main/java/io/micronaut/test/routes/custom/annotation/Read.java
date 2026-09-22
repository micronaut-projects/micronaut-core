package io.micronaut.test.routes.custom.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A route that reads: a GET request. Part of a made-up web framework, unknown to Micronaut.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Read {

    /**
     * @return The path, relative to the resource path
     */
    String value() default "";
}
