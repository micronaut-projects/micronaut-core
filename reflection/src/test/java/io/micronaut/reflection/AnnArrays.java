package io.micronaut.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation with an array member, to be implemented by hand: the proxy the compiler builds clones an array
 * member on every call, while an implementation written by hand and the one this module synthesizes answer the
 * same array every time, so the values a caller is handed are to carry a copy of it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface AnnArrays {

    String[] labels() default {};
}
