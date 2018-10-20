package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadata;

/**
 * Captures a generic {@link Argument}.
 * <p>
 * Example usage: <code>new GenericArgument&lt;List&lt;T&gt;&gt;() {}</code>
 *
 * @param <T> generic argument type
 */
public abstract class GenericArgument<T> extends DefaultArgument<T> {

    protected GenericArgument() {
        super(null, null, AnnotationMetadata.EMPTY_METADATA);
    }

}
