package io.micronaut.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation whose member an instance can fail to answer, as one naming a class absent from the class path
 * does: reading it throws {@code TypeNotPresentException} while a processor records the annotation all the same.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AnnBroken {
    String value();
}
