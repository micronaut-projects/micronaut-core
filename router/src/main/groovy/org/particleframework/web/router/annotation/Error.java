package org.particleframework.web.router.annotation;


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
     * @return The URI of the GET route if not specified inferred from the method name and arguments
     */
    HttpStatus value() default HttpStatus.INTERNAL_SERVER_ERROR;
}
