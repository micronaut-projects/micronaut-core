package io.micronaut.dev.compile.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class the {@link CountedProcessor} writes how many times it was initialized for.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Counted {

    /**
     * @return Whether the processor fails with an exception, as a broken processor does
     */
    boolean fail() default false;
}
