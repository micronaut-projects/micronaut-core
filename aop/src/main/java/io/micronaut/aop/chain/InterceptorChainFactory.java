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

import io.micronaut.aop.Intercepted;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Creates method and constructor interceptor chains using the context's interceptor registry.
 * Each bean context retains its own factory and interceptor registry.
 *
 * @since 5.3.0
 */
@Internal
@NullMarked
public final class InterceptorChainFactory {
    /** Constant for bean lookup. */
    public static final Argument<InterceptorChainFactory> ARGUMENT = Argument.of(InterceptorChainFactory.class);

    private final InterceptorRegistry registry;

    /**
     * @param registry The context's interceptor registry
     */
    public InterceptorChainFactory(InterceptorRegistry registry) {
        this.registry = registry;
    }

    /**
     * Builds a lifecycle chain using the retained candidates and this context's interceptor registry.
     * An empty candidate set is authoritative. Callers without retained candidates use the compatibility resolver.
     *
     * @param resolutionContext The resolution context
     * @param definition The bean definition
     * @param method The intercepted method
     * @param bean The bean instance
     * @param kind The interception kind
     * @param shared Explicit candidates from an older generated caller, or {@code null}
     * @param <T> The bean type
     * @param <R> The result type
     * @return A new lifecycle chain
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T, R> MethodInterceptorChain<T, R> build(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> definition,
        ExecutableMethod<T, R> method,
        T bean,
        InterceptorKind kind,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> shared) {
        // Empty means resolved, not a request to discover candidates again.
        Collection<BeanRegistration<Interceptor<?, ?>>> resolved = shared != null && !shared.isEmpty()
            ? shared : (Collection) resolutionContext.getBeanInterceptors(definition);
        if (resolved == null) {
            resolved = resolveLifecycleInterceptorRegistrations(resolutionContext, method, bean, kind);
        }
        return build(bean, method, (Collection) resolved, kind, ArrayUtils.EMPTY_OBJECT_ARRAY);
    }

    /**
     * Builds a method chain from already acquired candidates. Introduction chains also include around advice.
     *
     * @param bean The target
     * @param method The intercepted method
     * @param candidates The interceptor candidates
     * @param kind The interception kind
     * @param parameters The invocation arguments
     * @param <T> The bean type
     * @param <R> The result type
     * @return A new method chain
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T, R> MethodInterceptorChain<T, R> build(
        T bean,
        ExecutableMethod<T, R> method,
        Collection<BeanRegistration<Interceptor<T, ?>>> candidates,
        InterceptorKind kind,
        @Nullable Object... parameters) {
        Interceptor<T, ?>[] interceptors = registry.resolveInterceptors(method, candidates, kind);
        if (kind == InterceptorKind.INTRODUCTION) {
            interceptors = ArrayUtils.concat(registry.resolveInterceptors(method, candidates, InterceptorKind.AROUND), interceptors);
        }
        return new MethodInterceptorChain((Interceptor[]) interceptors, bean, method, kind, parameters);
    }

    /**
     * Builds a constructor chain, resolving candidates when an older caller has not supplied them.
     *
     * @param resolutionContext The resolution context
     * @param definition The bean definition
     * @param constructor The intercepted constructor
     * @param candidates Explicit interceptor candidates, or {@code null} to resolve them
     * @param additionalProxyConstructorParametersCount The internal proxy constructor argument count
     * @param parameters The complete constructor arguments
     * @param <T> The bean type
     * @return A new constructor chain
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> ConstructorInterceptorChain<T> build(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> definition,
        BeanConstructor<T> constructor,
        @Nullable Collection<BeanRegistration<Interceptor<T, T>>> candidates,
        int additionalProxyConstructorParametersCount,
        @Nullable Object... parameters) {
        if (candidates == null) {
            AnnotationMetadataHierarchy hierarchy = new AnnotationMetadataHierarchy(definition.getAnnotationMetadata(), constructor.getAnnotationMetadata());
            Collection<AnnotationValue<?>> bindings = AbstractInterceptorChain.resolveInterceptorValues(hierarchy, InterceptorKind.AROUND_CONSTRUCT);
            candidates = (Collection) resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, Qualifiers.byInterceptorBindingValues(bindings));
        }
        return new ConstructorInterceptorChain<>(definition, constructor, registry.resolveConstructorInterceptors(constructor, candidates),
            additionalProxyConstructorParametersCount, parameters);
    }

    /**
     * Candidate discovery for older generated definitions and direct callers without retained lifecycle state.
     * @param resolutionContext The resolution context
     * @param method The lifecycle method
     * @param bean The bean instance
     * @param kind The lifecycle kind
     * @return The legacy interceptor candidates
     * @since 5.3.0
     */
    private Collection<BeanRegistration<Interceptor<?, ?>>> resolveLifecycleInterceptorRegistrations(
        BeanResolutionContext resolutionContext,
        ExecutableMethod<?, ?> method,
        Object bean,
        InterceptorKind kind) {
        if (bean instanceof Intercepted intercepted && !intercepted.$interceptorRegistrations().isEmpty()) {
            return intercepted.$interceptorRegistrations();
        }
        Collection<AnnotationValue<?>> binding = AbstractInterceptorChain.resolveInterceptorValues(method.getAnnotationMetadata(), kind);
        return kind == InterceptorKind.PRE_DESTROY
            ? resolveLifecycleInterceptors(resolutionContext, binding)
            : resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, Qualifiers.byInterceptorBindingValues(binding));
    }

    /**
     * Resolves the interceptor candidates for pre-destroy interception.
     *
     * <p>The interceptors the container hands over when the bean was constructed with some are the candidate set.
     * Otherwise the interceptor instances among the dependents of the bean are, together with the singletons bound to
     * the event, and when the bean has none the interceptors are resolved by binding as the bean's own.</p>
     *
     * @param resolutionContext The resolution context
     * @param binding           The binding of the interception point
     * @return The interceptor registrations to select from
     * @since 5.2.0
     */
    @SuppressWarnings("unchecked")
    private Collection<BeanRegistration<Interceptor<?, ?>>> resolveLifecycleInterceptors(
        BeanResolutionContext resolutionContext,
        Collection<AnnotationValue<?>> binding) {

        Object attribute = resolutionContext.getAttribute(BeanResolutionContext.EXISTING_INTERCEPTOR_REGISTRATIONS);
        if (attribute instanceof List<?> existing) {
            return (List<BeanRegistration<Interceptor<?, ?>>>) existing;
        }
        Qualifier<Interceptor<?, ?>> qualifier = Qualifiers.byInterceptorBindingValues(binding);
        List<BeanRegistration<Interceptor<?, ?>>> existing = findExistingInterceptors(resolutionContext);
        if (existing.isEmpty()) {
            // resolved as the bean's own, which finds those created for an earlier interception point of the bean
            return resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, qualifier);
        }
        // The interceptor instances among the dependents of the bean, such as those a proxy retained and handed to
        // the target it destroys, are the candidates, as before. A singleton bound to the event is no dependent of
        // the bean, so it is added.
        List<BeanRegistration<Interceptor<?, ?>>> candidates = new ArrayList<>(existing);
        BeanContext beanContext = resolutionContext.getContext();
        for (BeanDefinition<Interceptor<?, ?>> definition : beanContext.getBeanDefinitions(Interceptor.ARGUMENT, qualifier)) {
            if (definition.isSingleton()) {
                candidates.add(beanContext.getBeanRegistration(definition));
            }
        }
        return candidates;
    }

    /**
     * Finds interceptor registrations already associated with a legacy disposal path. New bean registrations carry
     * the exact selected set through {@link BeanResolutionContext#EXISTING_INTERCEPTOR_REGISTRATIONS}; this fallback
     * remains for generated factory definitions that cannot transfer that set during construction.
     *
     * @param resolutionContext The resolution context
     * @return Existing interceptor registrations
     */
    @SuppressWarnings("unchecked")
    private List<BeanRegistration<Interceptor<?, ?>>> findExistingInterceptors(BeanResolutionContext resolutionContext) {
        List<BeanRegistration<?>> dependents = resolutionContext.getDependentBeans();
        if (dependents.isEmpty() && resolutionContext.getAttribute(BeanResolutionContext.EXISTING_DEPENDENT_BEANS) instanceof List<?> attribute) {
            dependents = (List<BeanRegistration<?>>) attribute;
        }
        if (dependents.isEmpty()) {
            return Collections.emptyList();
        }
        List<BeanRegistration<Interceptor<?, ?>>> interceptors = null;
        for (BeanRegistration<?> dependent : dependents) {
            if (dependent.getBean() instanceof Interceptor) {
                if (interceptors == null) {
                    interceptors = new ArrayList<>(dependents.size());
                }
                interceptors.add((BeanRegistration<Interceptor<?, ?>>) dependent);
            }
        }
        return interceptors == null ? Collections.emptyList() : interceptors;
    }
}
