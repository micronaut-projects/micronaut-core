package io.micronaut.context.annotation;

import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Qualifier that can be used on a bean to indicate it should match any qualifier or on a
 * injection point to indicate it should match any bean.
 *
 * @since 3.0.0
 * @author graemerocher
 */
@Qualifier
@Documented
@Retention(RUNTIME)
public @interface Any {
    String NAME = Any.class.getName();
}
