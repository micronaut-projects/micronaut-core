package io.micronaut.reflection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation type that is not public, as a specification nests one in a class of its own, and as a
 * constraint declared next to its validator is. The JDK defines the proxy of a type like this one in the
 * type's own package, and makes the proxy class package private too.
 */
@Retention(RetentionPolicy.RUNTIME)
@interface Restricted {

    int level() default 1;

    String name() default "unnamed";
}
