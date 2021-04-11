package io.micronaut.aop;

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.inject.ExecutableMethod;

import java.util.List;

/**
 * Strategy interface for looking up interceptors from the bean context.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public interface InterceptorRegistry {
    /**
     * Constant for bean lookup.
     */
    Argument<InterceptorRegistry> ARGUMENT = Argument.of(InterceptorRegistry.class);

    /**
     * Resolves method interceptors for the given method.
     *
     * @param method The method interceptors
     * @param interceptors The pre-resolved interceptors
     * @param interceptorKind The interceptor kind
     * @return An array of interceptors
     * @param <T> the bean type
     */
    @NonNull <T> Interceptor<T, ?>[] resolveInterceptors(
            @NonNull Executable<T, ?> method,
            @NonNull List<BeanRegistration<Interceptor<T, ?>>> interceptors,
            @NonNull InterceptorKind interceptorKind
    );

    /**
     * Resolves interceptors for the given constructor.
     *
     * @param constructor The constructor
     * @param interceptors The pre-resolved interceptors
     * @return An array of interceptors
     * @param <T> The bean type
     */
    @NonNull <T> Interceptor<T, T>[] resolveConstructorInterceptors(
            @NonNull BeanConstructor<T> constructor,
            @NonNull List<BeanRegistration<Interceptor<T, T>>> interceptors
    );
}
