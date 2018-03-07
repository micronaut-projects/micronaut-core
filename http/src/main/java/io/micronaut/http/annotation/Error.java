package io.micronaut.http.annotation;


import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.http.HttpStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation that can be applied to method to map it to an error route
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
@HttpMethodMapping
public @interface Error {
    /**
     * @return The exception to map to
     */
    Class<? extends Throwable> value() default Throwable.class;

    /**
     * @return The exception to map to
     */
    @AliasFor("value")
    Class<? extends Throwable> exception() default Throwable.class;

    /**
     * @return The {@link HttpStatus} code to map
     */
    HttpStatus status() default HttpStatus.INTERNAL_SERVER_ERROR;

    /**
     * Whether the error handler should be registered as a global error handler or just locally to the declaring {@link Controller}
     *
     * @return True if it should be global
     */
    boolean global() default false;
}
