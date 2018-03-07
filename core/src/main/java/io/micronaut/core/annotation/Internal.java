package io.micronaut.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotates a class or method regarded as internal and not for public consumption
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Internal {

}