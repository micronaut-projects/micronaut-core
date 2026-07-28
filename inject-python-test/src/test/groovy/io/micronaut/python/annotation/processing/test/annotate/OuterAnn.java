package io.micronaut.python.annotation.processing.test.annotate;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Meta-annotation used to test reading values from an annotation that is used as a stereotype.
 */
@Documented
@Retention(RUNTIME)
@Target({TYPE, ANNOTATION_TYPE})
public @interface OuterAnn {
    String value();
    int number() default 0;
}
