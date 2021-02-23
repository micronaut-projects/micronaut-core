package io.micronaut.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Repeatable wrapper for {@link InterceptorBinding}.
 *
 * @author graemerocher
 * @since 2.4.0
 * @see InterceptorBinding
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface InterceptorBindingDefinitions {
    /**
     * @return The interceptor binding definitions.
     */
    InterceptorBinding[] value();
}
