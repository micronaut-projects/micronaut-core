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

import io.micronaut.aop.ConstructorInvocationContext;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.InvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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

    /**
     * The constructor that is actually invoked. For a proxied bean this is the generated proxy constructor, which
     * declares the bean's own parameters followed by {@code additionalInterceptorParametersCount} internal ones.
     */
    private final BeanConstructor<T> beanConstructor;
    /**
     * The constructor made visible to interceptors: the constructor of the intercepted bean type with only the
     * parameters declared by the bean, so that it is consistent with {@link #getParameterValues()}.
     */
    private final BeanConstructor<T> interceptedConstructor;
    private final @Nullable Object[] internalParameters;

    /**
     * Default constructor.
     *
     * @param beanDefinition The bean constructor
     * @param beanConstructor The bean constructor
     * @param interceptors The interceptors
     * @param originalParameters The parameters
     * @param additionalInterceptorParametersCount The additional interceptor parameters count
     */
    @UsedByGeneratedCode
    private ConstructorInterceptorChain(
        BeanDefinition<T> beanDefinition,
        BeanConstructor<T> beanConstructor,
        Interceptor<T, T>[] interceptors,
        int additionalInterceptorParametersCount,
        @Nullable Object... originalParameters) {
        super(interceptors, resolveConcreteSubset(beanDefinition, originalParameters, additionalInterceptorParametersCount));
        this.beanConstructor = Objects.requireNonNull(beanConstructor, "Bean constructor cannot be null");
        this.internalParameters = resolveInterceptorArguments(beanDefinition, originalParameters, additionalInterceptorParametersCount);
        this.interceptedConstructor = resolveInterceptedConstructor(beanDefinition, beanConstructor, additionalInterceptorParametersCount, internalParameters);
    }

    @Override
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
            final @Nullable Object[] finalParameters;
            if (ArrayUtils.isNotEmpty(internalParameters)) {
                finalParameters = ArrayUtils.concat(getParameterValues(), internalParameters);
            } else {
                finalParameters = getParameterValues();
            }
            return beanConstructor.instantiate(finalParameters);
        } else {
            interceptor = this.interceptors[index++];
            if (LOG.isTraceEnabled()) {
                LOG.trace("Proceeded to next interceptor [{}] in chain for constructor invocation: {}", interceptor, interceptedConstructor.getDescription());
            }

            return Objects.requireNonNull(interceptor.intercept(this), "Constructor interceptor cannot return null");
        }
    }

    @Override
    public Argument<?>[] getArguments() {
        return interceptedConstructor.getArguments();
    }

    @Override
    public T invoke(T instance, @Nullable Object... arguments) {
        throw new UnsupportedOperationException("Existing instances cannot be invoked with Constructor injection");
    }

    @Override
    public BeanConstructor<T> getConstructor() {
        return interceptedConstructor;
    }

    /**
     * Internal methods that handles the logic of instantiating a bean that has constructor interception applied.
     *
     * @param resolutionContext The resolution context
     * @param beanContext The bean context
     * @param interceptors The interceptors. Can be null and if so should be resolved from the context.
     * @param definition The definition
     * @param constructor The bean constructor
     * @param parameters The resolved parameters
     * @param <T1> The bean type
     * @return The instantiated bean
     * @since 3.0.0
     */
    @Internal
    @UsedByGeneratedCode
    public static <T1> T1 instantiate(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        @Nullable List<BeanRegistration<Interceptor<T1, T1>>> interceptors,
        BeanDefinition<T1> definition,
        BeanConstructor<T1> constructor,
        @Nullable Object... parameters) {
        int micronaut3additionalProxyConstructorParametersCount = 3;
        return instantiate(resolutionContext, beanContext, interceptors, definition, constructor, micronaut3additionalProxyConstructorParametersCount, parameters);
    }

    /**
     * Internal methods that handles the logic of instantiating a bean that has constructor interception applied.
     *
     * @param resolutionContext The resolution context
     * @param beanContext The bean context
     * @param interceptors The interceptors. Can be null and if so should be resolved from the context.
     * @param definition The definition
     * @param constructor The bean constructor
     * @param additionalProxyConstructorParametersCount The additional proxy constructor parameters count
     * @param parameters The resolved parameters
     * @param <T1> The bean type
     * @return The instantiated bean
     * @since 3.0.0
     */
    @Internal
    @UsedByGeneratedCode
    public static <T1> T1 instantiate(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        @Nullable List<BeanRegistration<Interceptor<T1, T1>>> interceptors,
        BeanDefinition<T1> definition,
        BeanConstructor<T1> constructor,
        int additionalProxyConstructorParametersCount,
        @Nullable Object... parameters) {

        if (interceptors == null) {
            final AnnotationMetadataHierarchy hierarchy = new AnnotationMetadataHierarchy(definition.getAnnotationMetadata(), constructor.getAnnotationMetadata());
            final Collection<AnnotationValue<?>> annotationValues = resolveInterceptorValues(hierarchy, InterceptorKind.AROUND_CONSTRUCT);

            final Collection<BeanRegistration<Interceptor<?, ?>>> resolved = resolutionContext.getBeanRegistrations(
                Interceptor.ARGUMENT,
                Qualifiers.byInterceptorBindingValues(annotationValues)
            );
            interceptors = new ArrayList(resolved);
        }
        final InterceptorRegistry interceptorRegistry = beanContext.getBean(InterceptorRegistry.ARGUMENT);
        final Interceptor<T1, T1>[] resolvedInterceptors = interceptorRegistry
            .resolveConstructorInterceptors(constructor, interceptors);
        return Objects.requireNonNull(new ConstructorInterceptorChain<>(
            definition,
            constructor,
            resolvedInterceptors,
            additionalProxyConstructorParametersCount,
            parameters
        ).proceed(), "Constructor interceptor chain illegally returned null for constructor: " + constructor.getDescription());
    }

    private static @Nullable Object[] resolveConcreteSubset(BeanDefinition<?> beanDefinition,
                                                            @Nullable Object[] originalParameters,
                                                            int additionalProxyConstructorParametersCount) {
        if (beanDefinition instanceof AdvisedBeanType) {
            validateAdditionalProxyParameters(originalParameters, additionalProxyConstructorParametersCount);
            return Arrays.copyOfRange(
                originalParameters,
                0,
                originalParameters.length - additionalProxyConstructorParametersCount
            );
        }
        return originalParameters;
    }

    private static @Nullable Object[] resolveInterceptorArguments(BeanDefinition<?> beanDefinition,
                                                                  @Nullable Object[] originalParameters,
                                                                  int additionalProxyConstructorParametersCount) {
        if (beanDefinition instanceof AdvisedBeanType) {
            validateAdditionalProxyParameters(originalParameters, additionalProxyConstructorParametersCount);
            return Arrays.copyOfRange(
                originalParameters,
                originalParameters.length - additionalProxyConstructorParametersCount,
                originalParameters.length
            );
        }
        return originalParameters;
    }

    private static void validateAdditionalProxyParameters(@Nullable Object[] parameters,
                                                          int additionalProxyConstructorParametersCount) {
        // intercepted bean constructors include additional arguments in
        // addition to the arguments declared in the bean
        // Here we subtract these from the parameters made visible to the interceptor consumer
        if (parameters.length < additionalProxyConstructorParametersCount) {
            throw new IllegalStateException("Invalid intercepted bean constructor. This should never happen. Report an issue to the project maintainers.");
        }
    }

    /**
     * Resolves the constructor that interceptors see.
     *
     * <p>The constructor of a proxied bean is the generated proxy constructor: it declares the parameters of the
     * intercepted bean's constructor followed by the internal parameters the proxy needs. The parameter values are
     * already trimmed to the ones declared by the bean (see {@code resolveConcreteSubset}), so the constructor is
     * trimmed the same way to keep {@link #getConstructor()}, {@link #getArguments()} and
     * {@link #getDeclaringType()} consistent with {@link #getParameterValues()}.</p>
     *
     * @param beanDefinition The bean definition
     * @param beanConstructor The constructor that is invoked
     * @param additionalProxyConstructorParametersCount The additional proxy constructor parameters count
     * @param internalParameters The values of the additional proxy constructor parameters
     * @param <T> The bean type
     * @return The constructor to expose to interceptors
     */
    @SuppressWarnings("unchecked")
    private static <T> BeanConstructor<T> resolveInterceptedConstructor(BeanDefinition<T> beanDefinition,
                                                                        BeanConstructor<T> beanConstructor,
                                                                        int additionalProxyConstructorParametersCount,
                                                                        @Nullable Object[] internalParameters) {
        if (additionalProxyConstructorParametersCount > 0 && beanDefinition instanceof AdvisedBeanType<?> advisedBeanType) {
            Argument<?>[] proxyArguments = beanConstructor.getArguments();
            if (proxyArguments.length >= additionalProxyConstructorParametersCount) {
                return new InterceptedTargetConstructor<>(
                    beanConstructor,
                    (Class<T>) advisedBeanType.getInterceptedType(),
                    Arrays.copyOfRange(proxyArguments, 0, proxyArguments.length - additionalProxyConstructorParametersCount),
                    internalParameters
                );
            }
        }
        return beanConstructor;
    }

    /**
     * The view of a proxy constructor that describes the constructor of the intercepted bean type.
     *
     * @param <T> The bean type
     */
    private static final class InterceptedTargetConstructor<T> implements BeanConstructor<T> {

        private final BeanConstructor<T> proxyConstructor;
        private final Class<T> declaringBeanType;
        private final Argument<?>[] arguments;
        /**
         * The values of the internal parameters the proxy constructor declares after the bean's own ones. Never
         * empty: this view only exists when the proxy constructor declares such parameters.
         */
        private final @Nullable Object[] internalParameters;

        private InterceptedTargetConstructor(BeanConstructor<T> proxyConstructor,
                                             Class<T> declaringBeanType,
                                             Argument<?>[] arguments,
                                             @Nullable Object[] internalParameters) {
            this.proxyConstructor = proxyConstructor;
            this.declaringBeanType = declaringBeanType;
            this.arguments = arguments;
            this.internalParameters = internalParameters;
        }

        @Override
        public Class<T> getDeclaringBeanType() {
            return declaringBeanType;
        }

        @Override
        public Argument<?>[] getArguments() {
            return arguments;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return proxyConstructor.getAnnotationMetadata();
        }

        @Override
        public T instantiate(@Nullable Object... parameterValues) {
            return proxyConstructor.instantiate(ArrayUtils.concat(parameterValues, internalParameters));
        }

        @Override
        public String toString() {
            return getDescription();
        }
    }
}
