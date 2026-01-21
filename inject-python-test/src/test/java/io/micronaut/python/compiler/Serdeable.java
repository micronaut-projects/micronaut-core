package io.micronaut.python.compiler;

import io.micronaut.core.annotation.Introspected;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Test annotation with nested annotations to replicate the bug.
 */
@Serdeable.Serializable
@Serdeable.Deserializable
public @interface Serdeable {

    /**
     * Annotation used to indicate a type is serializable.
     */
    @Introspected
    @Retention(RetentionPolicy.RUNTIME)
    @interface Serializable {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Introspected
    @interface Deserializable {
    }
}
