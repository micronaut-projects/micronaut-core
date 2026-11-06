package io.micronaut.reflection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation type that depends only on JDK types, so a class loader whose parent is the platform loader can
 * define a copy of it. That copy cannot see {@code AnnotationValueProvider}, so the shared proxy, which implements
 * that interface as well, cannot be built for it.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Isolated {

    int level() default 1;

    String name() default "unnamed";
}
