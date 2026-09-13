package io.micronaut.reflection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation type of members a loader with no parent of its own resolves, so that a copy of it defined by such
 * a loader is a type the shared proxy cannot be built for: that proxy carries {@code AnnotationValueProvider} as
 * well, which a loader that does not see Micronaut cannot resolve.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Isolated {

    int level() default 1;

    String name() default "unnamed";
}
