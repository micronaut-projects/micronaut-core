package io.micronaut.aop;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanConstructor;

/**
 * An {@link InvocationContext} for construction invocation.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.0.0
 */
public interface ConstructorInvocationContext<T> extends InvocationContext<T, T> {
    /**
     * @return The bean type.
     */
    @NonNull
    BeanConstructor<T> getConstructor();
}
