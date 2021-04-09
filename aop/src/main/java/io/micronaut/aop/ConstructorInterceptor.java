package io.micronaut.aop;

import io.micronaut.core.annotation.NonNull;

/**
 * A {@link ConstructorInterceptor} extends the default {@link Interceptor} interface and allows intercepting constructors.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.0.0
 */
@FunctionalInterface
public interface ConstructorInterceptor<T> extends Interceptor<T, T> {

    /**
     * Extended version of the {@link #intercept(InvocationContext)} method that accepts a {@link ConstructorInvocationContext}.
     *
     * <p>It is illegal for constructor interceptors to return <code>null</code> and an exception will be thrown if this occurs.</p>
     *
     * @param context The context
     * @return The constructed object. Should never be <code>null</code>.
     */
    @NonNull T intercept(@NonNull ConstructorInvocationContext<T> context);

    @Override
    default @NonNull T intercept(@NonNull InvocationContext<T, T> context) {
        if (context instanceof ConstructorInvocationContext) {
            return intercept((ConstructorInvocationContext<T>) context);
        } else {
            throw new IllegalArgumentException("Context must be an instance of MethodInvocationContext");
        }
    }
}
