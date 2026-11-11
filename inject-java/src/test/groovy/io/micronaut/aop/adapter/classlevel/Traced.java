package io.micronaut.aop.adapter.classlevel;

import io.micronaut.aop.Around;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Around advice declared on an adapted SAM interface, which describes the adapter itself.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Around
public @interface Traced {
}
