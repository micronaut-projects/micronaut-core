package io.micronaut.annotation

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * Compiled ahead of the sources that use it, so its defaults are read back from its class file.
 */
@Retention(RetentionPolicy.RUNTIME)
@interface ArrayDefaults {

    Class<?> one() default int[].class

    Class<?>[] many() default [int[].class, String[][].class, java.util.UUID[][].class, String.class]
}
