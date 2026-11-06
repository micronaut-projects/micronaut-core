package io.micronaut.inject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A package private annotation, the shape Hibernate Validator allows for constraints declared next to their
 * validator. The JDK defines the proxy of a non public interface in that interface's own package and makes the
 * proxy class non public too.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@interface PackagePrivateExample {

    String value();

    String other() default "default";
}
