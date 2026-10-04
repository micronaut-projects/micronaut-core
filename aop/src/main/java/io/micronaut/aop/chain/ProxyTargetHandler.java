/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.aop.chain;

import io.micronaut.aop.Interceptor;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDependencyGroup;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What a proxy that fronts a separate target delegates to: it finds the target of a call, selects the
 * interceptors of the call and runs them. A generated proxy is injected with one, of the kind its declaration
 * asks for, and has no logic of its own.
 *
 * @param <T> The target type
 * @since 5.3.0
 */
@Internal
public interface ProxyTargetHandler<T> {
    /**
     * The name of the annotation on the handler parameter of a proxy constructor that holds the interceptor
     * binding of the proxy. It is not a qualifier: every proxy is injected with the same handler bean.
     */
    String BINDING = "io.micronaut.aop.chain.ProxyTargetHandler.Binding";

    /**
     * Binds the handler to the proxy it was injected into. Called once, from the constructor of the proxy.
     *
     * @param targetType The type of the target
     * @param introduction Whether the proxy is an introduction
     * @param perTarget Whether the interceptors of a call are those of its target
     * @param methodNames The names of the proxied methods
     * @param methodArguments The argument types of the proxied methods
     */
    @UsedByGeneratedCode
    void bind(Argument<T> targetType, boolean introduction, boolean perTarget, String[] methodNames, Class<?>[][] methodArguments);

    /**
     * Runs one call of the proxy.
     *
     * @param index The index of the proxied method
     * @param arguments The arguments of the call
     * @return What the call returns
     */
    @UsedByGeneratedCode
    @Nullable Object invoke(int index, Object[] arguments);

    /**
     * @return The target of a call made now
     */
    @UsedByGeneratedCode
    T target();

    /**
     * @return Whether the handler holds a target
     */
    @UsedByGeneratedCode
    boolean hasCachedTarget();

    /** Forgets the target the handler holds, if it can resolve another. */
    @UsedByGeneratedCode
    void clearCachedTarget();

    /**
     * @return The registration of the target the handler holds, or null
     */
    @UsedByGeneratedCode
    @Nullable BeanRegistration<T> targetRegistration();

    /**
     * @param qualifier The qualifier later lookups of the target use
     */
    @UsedByGeneratedCode
    void withQualifier(@Nullable Qualifier<T> qualifier);

    /**
     * @return The dependencies of the proxy
     */
    @UsedByGeneratedCode
    @Nullable BeanDependencyGroup dependencies();

    /**
     * @return A copy of the proxied methods
     */
    @UsedByGeneratedCode
    ExecutableMethod<?, ?>[] interceptedMethods();

    /**
     * @return The interceptors the proxy was created with
     */
    @UsedByGeneratedCode
    List<BeanRegistration<Interceptor<?, ?>>> interceptorRegistrations();

    /**
     * What a handler is created from: the injection point it is injected at, which is the constructor of a proxy.
     *
     * @param chainFactory The chain factory of the context
     * @param binding The binding the interceptors of the proxy are qualified by, or null when it binds none
     * @param resolutionContext The context the proxy is created in
     * @param beanContext The bean context
     * @param qualifier The qualifier the proxy is created with, which is that of its target
     */
    record Creation(InterceptorChainFactory chainFactory,
                           @Nullable Qualifier<Interceptor<?, ?>> binding,
                           BeanResolutionContext resolutionContext,
                           BeanContext beanContext,
                           @Nullable Qualifier<?> qualifier) {
    }
}
