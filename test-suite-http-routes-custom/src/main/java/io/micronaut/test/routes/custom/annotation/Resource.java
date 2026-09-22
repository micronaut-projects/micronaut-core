package io.micronaut.test.routes.custom.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The base path of the routes of a resource. Part of a made-up web framework, unknown to Micronaut.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Resource {

    /**
     * @return The path
     */
    String value();
}
