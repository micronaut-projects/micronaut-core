package io.micronaut.inject.annotation

import io.micronaut.core.annotation.Retainable

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * A retainable annotation for {@link RetainableSpec}, compiled ahead of the sources under test because the Groovy
 * frontend does not resolve an annotation declared in the same source as a stereotype.
 */
@Retainable
@Retention(RetentionPolicy.RUNTIME)
@interface Limit {
    int min() default 0
    int max() default 100
}
