package io.micronaut.inject.visitor;

// tag::imports[]
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Bean;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
// end::imports[]

/**
 * @author graemerocher
 * @since 1.0
 */
// tag::class[]
@Introduction // <1>
@Bean // <3>
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD})
public @interface Stub {
    String value() default "";
}
// end::class[]
