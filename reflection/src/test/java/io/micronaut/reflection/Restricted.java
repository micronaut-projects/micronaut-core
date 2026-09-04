package io.micronaut.reflection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation type that is not public, as a specification nests one in a class of its own. The proxy the
 * shared metadata support builds carries {@code AnnotationValueProvider} as well, which cannot be done for a
 * type like this one.
 */
@Retention(RetentionPolicy.RUNTIME)
@interface Restricted {

    int level() default 1;

    String name() default "unnamed";
}
