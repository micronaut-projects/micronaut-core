package io.micronaut.python.annotation.processing.test.annotate;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation annotated with {@link OuterAnn} to test meta-annotation value propagation.
 */
@Documented
@Retention(RUNTIME)
@Target({TYPE, ANNOTATION_TYPE})
@OuterAnn(value = "stereo", number = 99)
public @interface InnerAnn {
}
