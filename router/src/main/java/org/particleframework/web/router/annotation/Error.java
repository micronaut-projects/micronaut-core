package org.particleframework.web.router.annotation;


import org.particleframework.context.annotation.AliasFor;
import org.particleframework.http.HttpStatus;

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
@Action
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
}
