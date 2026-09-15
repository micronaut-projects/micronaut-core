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

import io.micronaut.aop.Intercepted;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.ConstructorAdviceException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
/**
 * Runs the interception of a bean's lifecycle events: its construction, its post-construct and its pre-destroy.
 *
 * <p>Each event resolves the interceptors bound to it as the bean's own, through
 * {@link BeanResolutionContext#getInterceptorRegistrations(io.micronaut.core.type.Argument, io.micronaut.context.Qualifier)},
 * selects the ones that apply through the {@link InterceptorRegistry}, and runs the chain of the event. The chains
 * themselves, {@link ConstructorInterceptorChain} and {@link MethodInterceptorChain}, only run.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class LifecycleInterception {

    /**
     * The internal constructor parameters a generated proxy appends after the bean's own: the resolution context,
     * the bean context and the qualifier.
     */
    public static final int PROXY_CONSTRUCTOR_PARAMETERS = 3;

    private LifecycleInterception() {
    }

    /**
     * The interceptors a bean's construction, post-construct and pre-destroy interception all select from: the
     * bean's own, resolved once for construction by every binding the bean declares, whatever kind each was declared
     * for, so that the non-singleton ones are created with the bean and found again by its later interception points.
     *
     * <p>A proxy fronting a separate target never reaches here: the definition writer intercepts the construction of
     * the target alone, as CDI never intercepts the construction of a client proxy.</p>
     *
     * @param resolutionContext The resolution context
     * @param constructor       The constructor, whose metadata is the bean's combined with the constructor's
     * @param <T>               The bean type
     * @return The interceptors, or {@code null} when the bean binds none
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    public static <T> List<BeanRegistration<Interceptor<T, T>>> constructionInterceptors(BeanResolutionContext resolutionContext,
                                                                                        AnnotationMetadataProvider constructor) {
        AnnotationMetadata metadata = constructor.getAnnotationMetadata();
        if (metadata.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING).isEmpty()) {
            return null;
        }
        return new ArrayList(resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(metadata)));
    }

    /**
     * Instantiates a bean whose construction is intercepted, a generated proxy with the three internal constructor
     * parameters it appends.
     *
     * @param resolutionContext The resolution context
     * @param beanContext       The bean context
     * @param interceptors      The interceptors, as
     *                          {@link #constructionInterceptors(BeanResolutionContext, AnnotationMetadataProvider)}
     *                          resolves them, or {@code null} to resolve them here
     * @param definition        The definition
     * @param constructor       The bean constructor
     * @param parameters        The resolved parameters
     * @param <T>               The bean type
     * @return The instantiated bean
     */
    public static <T> T instantiate(BeanResolutionContext resolutionContext,
                                    BeanContext beanContext,
                                    @Nullable List<BeanRegistration<Interceptor<T, T>>> interceptors,
                                    BeanDefinition<T> definition,
                                    BeanConstructor<T> constructor,
                                    @Nullable Object... parameters) {
        return instantiate(resolutionContext, beanContext, interceptors, definition, constructor, PROXY_CONSTRUCTOR_PARAMETERS, parameters);
    }

    /**
     * Instantiates a bean whose construction is intercepted, with the given number of internal constructor
     * parameters. Only the deprecated entry points on the chains, which bean definitions compiled by earlier
     * versions call with the count they were built with, need a count other than the default.
     *
     * @param resolutionContext                         The resolution context
     * @param beanContext                               The bean context
     * @param interceptors                              The interceptors, or {@code null} to resolve them as the
     *                                                  bean's own
     * @param definition                                The definition
     * @param constructor                               The bean constructor
     * @param additionalProxyConstructorParametersCount The internal constructor parameters the proxy appends
     * @param parameters                                The resolved parameters
     * @param <T>                                       The bean type
     * @return The instantiated bean
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> T instantiate(BeanResolutionContext resolutionContext,
                             BeanContext beanContext,
                             @Nullable List<BeanRegistration<Interceptor<T, T>>> interceptors,
                             BeanDefinition<T> definition,
                             BeanConstructor<T> constructor,
                             int additionalProxyConstructorParametersCount,
                             @Nullable Object... parameters) {
        if (interceptors == null) {
            interceptors = constructionInterceptors(resolutionContext, constructor);
        }
        if (interceptors == null) {
            // no binding at all, so nothing can qualify: the constructor runs unadvised
            interceptors = List.of();
        }
        final InterceptorRegistry interceptorRegistry = beanContext.getBean(InterceptorRegistry.ARGUMENT);
        final Interceptor<T, T>[] resolvedInterceptors = interceptorRegistry.resolveConstructorInterceptors(constructor, interceptors);
        ConstructorInterceptorChain<T> chain = new ConstructorInterceptorChain<>(
            definition,
            constructor,
            resolvedInterceptors,
            additionalProxyConstructorParametersCount,
            parameters
        );
        T bean;
        try {
            bean = chain.proceed();
        } catch (ConstructorAdviceException e) {
            // Already carried, by the advice around a bean this one's construction depends on
            throw e;
        } catch (RuntimeException e) {
            if (e == chain.bodyFailure) {
                // The constructor's own body threw. Keep the wrapping an unadvised constructor's throwable gets
                throw e;
            }
            // The advice around the constructor threw. Advice around a method reaches its caller as it was
            // thrown; carry this one so that construction advice does too
            throw new ConstructorAdviceException(e);
        }
        return Objects.requireNonNull(bean, "Constructor interceptor chain illegally returned null for constructor: " + constructor.getDescription());
    }

    /**
     * Runs the {@link InterceptorKind#POST_CONSTRUCT} interception of a bean.
     *
     * @param resolutionContext   The resolution context
     * @param beanContext         The bean context
     * @param definition          The definition
     * @param postConstructMethod The post construct method
     * @param bean                The bean
     * @param <T>                 The bean type
     * @return The bean instance
     */
    @Nullable
    public static <T> T initialize(BeanResolutionContext resolutionContext,
                                   BeanContext beanContext,
                                   BeanDefinition<T> definition,
                                   ExecutableMethod<T, T> postConstructMethod,
                                   T bean) {
        return intercept(resolutionContext, beanContext, definition, postConstructMethod, bean, InterceptorKind.POST_CONSTRUCT, null);
    }

    /**
     * Runs the {@link InterceptorKind#POST_CONSTRUCT} interception of a bean with registrations a caller resolved,
     * as a definition compiled by 5.2 hands them; the deprecated entry point on the chain delegates here.
     */
    @Nullable
    static <T> T initialize(BeanResolutionContext resolutionContext,
                            BeanContext beanContext,
                            BeanDefinition<T> definition,
                            ExecutableMethod<T, T> postConstructMethod,
                            T bean,
                            @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> handed) {
        return intercept(resolutionContext, beanContext, definition, postConstructMethod, bean, InterceptorKind.POST_CONSTRUCT, handed);
    }

    /**
     * Runs the {@link InterceptorKind#PRE_DESTROY} interception of a bean.
     *
     * @param resolutionContext The resolution context
     * @param beanContext       The bean context
     * @param definition        The definition
     * @param preDestroyMethod  The pre destroy method
     * @param bean              The bean
     * @param <T>               The bean type
     * @return The bean instance
     */
    @Nullable
    public static <T> T dispose(BeanResolutionContext resolutionContext,
                                BeanContext beanContext,
                                BeanDefinition<T> definition,
                                ExecutableMethod<T, T> preDestroyMethod,
                                T bean) {
        return intercept(resolutionContext, beanContext, definition, preDestroyMethod, bean, InterceptorKind.PRE_DESTROY, null);
    }

    /**
     * Runs the {@link InterceptorKind#PRE_DESTROY} interception of a bean with registrations a caller resolved; the
     * deprecated entry point on the chain delegates here.
     */
    @Nullable
    static <T> T dispose(BeanResolutionContext resolutionContext,
                         BeanContext beanContext,
                         BeanDefinition<T> definition,
                         ExecutableMethod<T, T> preDestroyMethod,
                         T bean,
                         @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> handed) {
        return intercept(resolutionContext, beanContext, definition, preDestroyMethod, bean, InterceptorKind.PRE_DESTROY, handed);
    }

    @SuppressWarnings({"unchecked", "rawtypes", "removal"})
    @Nullable
    private static <T> T intercept(BeanResolutionContext resolutionContext,
                                   BeanContext beanContext,
                                   BeanDefinition<T> definition,
                                   ExecutableMethod<T, T> interceptedMethod,
                                   T bean,
                                   InterceptorKind kind,
                                   @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> handed) {
        final AnnotationMetadata annotationMetadata = interceptedMethod.getAnnotationMetadata();
        final Collection<AnnotationValue<?>> binding = AbstractInterceptorChain.resolveInterceptorValues(annotationMetadata, kind);

        final Collection<BeanRegistration<Interceptor<?, ?>>> resolved;
        if (handed != null && !handed.isEmpty()) {
            // resolved by a caller compiled against 5.2 and handed over
            resolved = handed;
        } else if (bean instanceof Intercepted intercepted && !intercepted.$interceptorRegistrations().isEmpty()) {
            // A proxy generated before 5.3 retained the registrations it was constructed with; a newer proxy returns
            // none here, its interceptors being dependents of the bean like everyone else's.
            resolved = intercepted.$interceptorRegistrations();
        } else {
            // Resolved by binding as the bean's own: a non-singleton interceptor is the instance among the bean's
            // dependents, which the context carries while the bean is created and again while it is destroyed, or is
            // created there the first time.
            resolved = resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, Qualifiers.byInterceptorBindingValues(binding));
        }
        final InterceptorRegistry interceptorRegistry = beanContext.getBean(InterceptorRegistry.ARGUMENT);
        final Interceptor[] resolvedInterceptors = interceptorRegistry.resolveInterceptors((ExecutableMethod) interceptedMethod, (Collection) resolved, kind);
        if (ArrayUtils.isNotEmpty(resolvedInterceptors)) {
            final MethodInterceptorChain<T, T> chain = new MethodInterceptorChain<>(resolvedInterceptors, bean, interceptedMethod, kind);
            return Objects.requireNonNull(
                chain.proceed(),
                kind.name() + " interceptor chain illegal returned null for type: " + definition.getBeanType()
            );
        }
        return interceptedMethod.invoke(bean);
    }
}
