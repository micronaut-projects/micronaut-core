package io.micronaut.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Allows intercepting the bean constructor.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
public @interface AroundConstruct  {
}
