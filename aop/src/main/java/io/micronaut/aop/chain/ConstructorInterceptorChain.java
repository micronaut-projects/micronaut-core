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

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.ConstructorAdviceException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.TargetConstructorCache;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;

import java.lang.reflect.Constructor;
import java.util.Arrays;
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
public final class ConstructorInterceptorChain<T> extends AbstractInterceptorChain<T, T> implements ConstructorInvocation<T> {

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
     * The exception the intercepted constructor's own body threw, if it threw one. Kept so that
     * {@link #instantiate} can tell it apart from an exception thrown by the advice around it: the two are
     * propagated differently.
     */
    private @Nullable RuntimeException bodyFailure;

    /**
     * Default constructor.
     *
     * @param beanDefinition The bean constructor
     * @param beanConstructor The bean constructor
     * @param interceptors The interceptors
     * @param declaredParameters The arguments the bean declares, which interceptors see and may change
     * @param internalParameters The internal arguments a proxy constructor declares after them, empty otherwise
     */
    public ConstructorInterceptorChain(
        BeanDefinition<T> beanDefinition,
        BeanConstructor<T> beanConstructor,
        Interceptor<T, T>[] interceptors,
        @Nullable Object[] declaredParameters,
        @Nullable Object[] internalParameters) {
        super(interceptors, declaredParameters);
        this.beanConstructor = Objects.requireNonNull(beanConstructor, "Bean constructor cannot be null");
        this.internalParameters = internalParameters;
        this.interceptedConstructor = resolveInterceptedConstructor(beanDefinition, beanConstructor, internalParameters);
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
            if (internalParameters.length > 0) {
                finalParameters = ArrayUtils.concat(getParameterValues(), internalParameters);
            } else {
                finalParameters = getParameterValues();
            }
            try {
                return beanConstructor.instantiate(finalParameters);
            } catch (RuntimeException e) {
                bodyFailure = e;
                throw e;
            }
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
     * @deprecated Bean definitions instantiate through {@link InterceptorChainFactory#instantiate}. Kept for definitions compiled by earlier versions.
     */
    @Deprecated(since = "5.3.0")
    @Internal
    @UsedByGeneratedCode
    public static <T1> T1 instantiate(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        @Nullable List<BeanRegistration<Interceptor<T1, T1>>> interceptors,
        BeanDefinition<T1> definition,
        BeanConstructor<T1> constructor,
        @Nullable Object... parameters) {
        LegacyGeneratedCode.warn("ConstructorInterceptorChain.instantiate");
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
     * @deprecated Bean definitions instantiate through {@link InterceptorChainFactory#instantiate}. Kept for definitions compiled by earlier versions.
     */
    @Deprecated(since = "5.3.0")
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
        LegacyGeneratedCode.warn("ConstructorInterceptorChain.instantiate");

        // Callers compiled by earlier versions pass a count for every definition; only a proxy declares internal arguments.
        int internalCount = definition instanceof AdvisedBeanType ? additionalProxyConstructorParametersCount : 0;
        return beanContext.getBean(InterceptorChainFactory.ARGUMENT).instantiate(
            resolutionContext, definition, constructor, interceptors, internalCount, parameters
        );
    }

    /**
     * Executes construction advice, preserving the distinction between constructor-body and advice failures.
     * Unlike {@link #proceed()}, this entry point carries advice failures to the bean creation boundary and
     * rejects a null construction result even when no advice matches.
     * @return The constructed bean
     * @since 5.3.0
     */
    @Override
    public T instantiate() {
        T bean;
        try {
            bean = proceed();
        } catch (ConstructorAdviceException e) {
            // Already carried, by the advice around a bean this one's construction depends on
            throw e;
        } catch (RuntimeException e) {
            if (e == bodyFailure) {
                // The constructor's own body threw. Keep the wrapping an unadvised constructor's throwable gets
                throw e;
            }
            // The advice around the constructor threw. Advice around a method reaches its caller as it was
            // thrown; carry this one so that construction advice does too
            throw new ConstructorAdviceException(e);
        }
        return Objects.requireNonNull(bean, "Constructor interceptor chain illegally returned null for constructor: " + beanConstructor.getDescription());
    }

    /**
     * Resolves the constructor that interceptors see.
     *
     * <p>The constructor of a proxied bean is the generated proxy constructor: it declares the parameters of the
     * intercepted bean's constructor followed by the internal parameters the proxy needs. The parameter values are
     * already trimmed to the ones declared by the bean, so the constructor is
     * trimmed the same way to keep {@link #getConstructor()}, {@link #getArguments()} and
     * {@link #getDeclaringType()} consistent with {@link #getParameterValues()}.</p>
     *
     * @param beanDefinition The bean definition
     * @param beanConstructor The constructor that is invoked
     * @param internalParameters The values of the additional proxy constructor parameters
     * @param <T> The bean type
     * @return The constructor to expose to interceptors
     */
    @SuppressWarnings("unchecked")
    private static <T> BeanConstructor<T> resolveInterceptedConstructor(BeanDefinition<T> beanDefinition,
                                                                        BeanConstructor<T> beanConstructor,
                                                                        @Nullable Object[] internalParameters) {
        if (internalParameters.length > 0 && beanDefinition instanceof AdvisedBeanType<?> advisedBeanType) {
            Argument<?>[] proxyArguments = beanConstructor.getArguments();
            if (proxyArguments.length >= internalParameters.length) {
                return new InterceptedTargetConstructor<>(
                    beanConstructor,
                    (Class<T>) advisedBeanType.getInterceptedType(),
                    Arrays.copyOfRange(proxyArguments, 0, proxyArguments.length - internalParameters.length),
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
        private final TargetConstructorCache<T> targetConstructor = new TargetConstructorCache<>();

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
        public @Nullable Constructor<T> getTargetConstructor() {
            // Resolved against the intercepted type and the parameters the bean declares, not the proxy
            // constructor, which appends the internal parameters
            return targetConstructor.get(() -> TargetConstructorCache.resolve(this));
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
