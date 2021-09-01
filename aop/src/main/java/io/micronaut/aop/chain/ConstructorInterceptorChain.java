/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.aop.*;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.*;

/**
 * Implementation of {@link InvocationContext} for constructor interception.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
@UsedByGeneratedCode
public final class ConstructorInterceptorChain<T> extends AbstractInterceptorChain<T, T> implements ConstructorInvocationContext<T> {

    private final BeanConstructor<T> beanConstructor;
    private Object[] internalParameters = ArrayUtils.EMPTY_OBJECT_ARRAY;

    /**
     * Default constructor.
     *
     * @param beanConstructor    The bean constructor
     * @param interceptors       The method interceptors to be passed to the final object to be constructed
     * @param originalParameters The parameters
     */
    private ConstructorInterceptorChain(
            @NonNull BeanConstructor<T> beanConstructor,
            @NonNull Interceptor<T, T>[] interceptors,
            Object... originalParameters) {
        super(interceptors, originalParameters);
        this.beanConstructor = Objects.requireNonNull(beanConstructor, "Bean constructor cannot be null");
    }

    /**
     * Default constructor.
     *
     * @param beanDefinition                       The bean constructor
     * @param beanConstructor                      The bean constructor
     * @param interceptors                         The interceptors
     * @param originalParameters                   The parameters
     * @param additionalInterceptorParametersCount The additional interceptor parameters count
     */
    @UsedByGeneratedCode
    private ConstructorInterceptorChain(
            @NonNull BeanDefinition<T> beanDefinition,
            @NonNull BeanConstructor<T> beanConstructor,
            @NonNull Interceptor<T, T>[] interceptors,
            int additionalInterceptorParametersCount,
            Object... originalParameters) {
        this(beanConstructor, interceptors, resolveConcreteSubset(beanDefinition, originalParameters, additionalInterceptorParametersCount));
        internalParameters = resolveInterceptorArguments(beanDefinition, originalParameters, additionalInterceptorParametersCount);
    }

    @Override
    @NonNull
    public InterceptorKind getKind() {
        return InterceptorKind.AROUND_CONSTRUCT;
    }

    @Override
    public T getTarget() {
        throw new UnsupportedOperationException("The target cannot be retrieved for Constructor interception");
    }

    @Override
    public T proceed() throws RuntimeException {
        Interceptor<T, T> interceptor;
        if (interceptorCount == 0 || index == interceptorCount) {
            final Object[] finalParameters;
            if (ArrayUtils.isNotEmpty(internalParameters)) {
                finalParameters = ArrayUtils.concat(getParameterValues(), internalParameters);
            } else {
                finalParameters = getParameterValues();
            }
            return beanConstructor.instantiate(finalParameters);
        } else {
            interceptor = this.interceptors[index++];
            if (LOG.isTraceEnabled()) {
                LOG.trace("Proceeded to next interceptor [{}] in chain for constructor invocation: {}", interceptor, beanConstructor);
            }

            return interceptor.intercept(this);
        }
    }

    @Override
    public @NonNull
    Argument<?>[] getArguments() {
        return beanConstructor.getArguments();
    }

    @Override
    public T invoke(T instance, Object... arguments) {
        throw new UnsupportedOperationException("Existing instances cannot be invoked with Constructor injection");
    }

    @Override
    @NonNull
    public BeanConstructor<T> getConstructor() {
        return beanConstructor;
    }

    /**
     * Internal methods that handles the logic of instantiating a bean that has constructor interception applied.
     *
     * @param resolutionContext The resolution context
     * @param beanContext       The bean context
     * @param interceptors      The interceptors. Can be null and if so should be resolved from the context.
     * @param definition        The definition
     * @param constructor       The bean constructor
     * @param parameters        Th resolved parameters
     * @param <T1>              The bean type
     * @return The instantiated bean
     * @since 3.0.0
     */
    @Internal
    @UsedByGeneratedCode
    @NonNull
    @Deprecated
    public static <T1> T1 instantiate(
            @NonNull BeanResolutionContext resolutionContext,
            @NonNull BeanContext beanContext,
            @Nullable List<BeanRegistration<Interceptor<T1, T1>>> interceptors,
            @NonNull BeanDefinition<T1> definition,
            @NonNull BeanConstructor<T1> constructor,
            @NonNull Object... parameters) {
        int micronaut3additionalProxyConstructorParametersCount = 3;
        return instantiate(resolutionContext, beanContext, interceptors, definition, constructor, micronaut3additionalProxyConstructorParametersCount, parameters);
    }

    /**
     * Internal methods that handles the logic of instantiating a bean that has constructor interception applied.
     *
     * @param resolutionContext                         The resolution context
     * @param beanContext                               The bean context
     * @param interceptors                              The interceptors. Can be null and if so should be resolved from the context.
     * @param definition                                The definition
     * @param constructor                               The bean constructor
     * @param additionalProxyConstructorParametersCount The additional proxy constructor parameters count
     * @param parameters                                The resolved parameters
     * @param <T1>                                      The bean type
     * @return The instantiated bean
     * @since 3.0.0
     */
    @Internal
    @UsedByGeneratedCode
    @NonNull
    public static <T1> T1 instantiate(
            @NonNull BeanResolutionContext resolutionContext,
            @NonNull BeanContext beanContext,
            @Nullable List<BeanRegistration<Interceptor<T1, T1>>> interceptors,
            @NonNull BeanDefinition<T1> definition,
            @NonNull BeanConstructor<T1> constructor,
            int additionalProxyConstructorParametersCount,
            @NonNull Object... parameters) {

        if (interceptors == null) {
            final AnnotationMetadataHierarchy hierarchy = new AnnotationMetadataHierarchy(definition.getAnnotationMetadata(), constructor.getAnnotationMetadata());
            final List<String> annotationNames = InterceptorBindingQualifier.resolveInterceptorValues(hierarchy);

            final Collection<BeanRegistration<Interceptor<?, ?>>> resolved = ((DefaultBeanContext) beanContext).getBeanRegistrations(
                    resolutionContext,
                    Interceptor.ARGUMENT,
                    Qualifiers.byInterceptorBinding(annotationNames)
            );
            interceptors = new ArrayList(resolved);
        }
        final InterceptorRegistry interceptorRegistry = beanContext.getBean(InterceptorRegistry.ARGUMENT);
        final Interceptor<T1, T1>[] resolvedInterceptors = interceptorRegistry
                .resolveConstructorInterceptors(constructor, interceptors);
        return Objects.requireNonNull(new ConstructorInterceptorChain<T1>(
                definition,
                constructor,
                resolvedInterceptors,
                additionalProxyConstructorParametersCount,
                parameters
        ).proceed(), "Constructor interceptor chain illegally returned null for constructor: " + constructor.getDescription());
    }

    private static Object[] resolveConcreteSubset(BeanDefinition<?> beanDefinition,
                                                  Object[] originalParameters,
                                                  int additionalProxyConstructorParametersCount) {

        if (beanDefinition instanceof AdvisedBeanType) {

            // intercepted bean constructors include additional arguments in
            // addition to the arguments declared in the bean
            // Here we subtract these from the parameters made visible to the interceptor consumer
            if (originalParameters.length < additionalProxyConstructorParametersCount) {
                throw new IllegalStateException("Invalid intercepted bean constructor. This should never happen. Report an issue to the project maintainers.");
            }
            return Arrays.copyOfRange(
                    originalParameters,
                    0,
                    originalParameters.length - additionalProxyConstructorParametersCount
            );
        }
        return originalParameters;
    }

    private static Object[] resolveInterceptorArguments(BeanDefinition<?> beanDefinition,
                                                        Object[] originalParameters,
                                                        int additionalProxyConstructorParametersCount) {

        if (beanDefinition instanceof AdvisedBeanType) {

            // intercepted bean constructors include additional arguments in
            // addition to the arguments declared in the bean
            // Here we subtract these from the parameters made visible to the interceptor consumer
            if (originalParameters.length < additionalProxyConstructorParametersCount) {
                throw new IllegalStateException("Invalid intercepted bean constructor. This should never happen. Report an issue to the project maintainers.");
            }
            return Arrays.copyOfRange(
                    originalParameters,
                    originalParameters.length - additionalProxyConstructorParametersCount,
                    originalParameters.length
            );
        }
        return originalParameters;
    }
}
