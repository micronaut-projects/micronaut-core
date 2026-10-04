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
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.context.BeanDefinitionRegistry;
import io.micronaut.context.BeanDependencyGroup;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.proxy.InterceptedBean;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Acquires interceptor candidates through their dependency owner and selects from them, independently of
 * invocation construction. An {@link InterceptorChainFactory} returns a subclass from
 * {@link InterceptorChainFactory#candidateResolver()} to customize acquisition.
 *
 * @since 5.3.0
 */
@Internal
public class InterceptorCandidateResolver {
    private final InterceptorRegistry registry;
    // asked in order: what creation retained first, then discovery for definitions that retained nothing
    private final List<LifecycleCandidateSource> lifecycleSources = List.of(
        new RetainedLifecycleCandidates(), new LegacyLifecycleCandidates());

    /**
     * @param registry The selection strategy used after candidate acquisition
     */
    public InterceptorCandidateResolver(InterceptorRegistry registry) {
        this.registry = registry;
    }

    /**
     * Selects the interceptors for a method invocation. Introduction invocations execute around advice before
     * introduction advice. The selection can be retained and reused by independent invocations.
     *
     * @param method The intercepted method
     * @param candidates The acquired interceptor registrations
     * @param kind The interception kind
     * @param <T> The bean type
     * @return The selected interceptors in invocation order
     */
    @UsedByGeneratedCode
    public <T> Interceptor<T, ?>[] selectMethodInterceptors(
        ExecutableMethod<T, ?> method,
        Collection<BeanRegistration<Interceptor<T, ?>>> candidates,
        InterceptorKind kind) {
        Interceptor<T, ?>[] selected = registry.resolveInterceptors(method, candidates, kind);
        if (kind != InterceptorKind.INTRODUCTION) {
            return selected;
        }
        return ArrayUtils.concat(registry.resolveInterceptors(method, candidates, InterceptorKind.AROUND), selected);
    }

    /**
     * Acquires the combined construction and lifecycle candidates described by a bean's constructor metadata.
     * @param resolutionContext The creation context
     * @param constructor The constructor with combined bean and constructor metadata
     * @param <T> The bean type
     * @return The candidates, or null when the bean declares no binding
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> @Nullable List<BeanRegistration<Interceptor<T, T>>> resolveBeanCandidates(
        BeanResolutionContext resolutionContext, AnnotationMetadataProvider constructor) {
        AnnotationMetadata metadata = constructor.getAnnotationMetadata();
        if (metadata.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING).isEmpty()) {
            return null;
        }
        return new ArrayList(resolutionContext.getInterceptorRegistrations(
            Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(metadata)));
    }

    /**
     * Retains all lifecycle candidates before injection and initialization, including an empty result.
     * Destruction-only targets can borrow their owning proxy's candidates; initialization keeps its own set.
     * @param resolutionContext The creation context
     * @param definition The bean definition
     * @param bean The constructed instance
     * @param initialization Whether post-construct interception needs its own candidates
     */
    @UsedByGeneratedCode
    public void captureLifecycleCandidates(BeanResolutionContext resolutionContext, BeanDefinition<?> definition,
                                           @Nullable Object bean, boolean initialization) {
        if (bean == null || resolutionContext.getBeanInterceptors(definition) != null) {
            return;
        }
        if (!initialization) {
            List<?> destructionCandidates = resolutionContext.getBeanDestructionInterceptors(definition);
            if (destructionCandidates != null) {
                resolutionContext.setBeanInterceptors(definition, destructionCandidates);
                return;
            }
        }
        List<?> candidates = bean instanceof InterceptedBean proxy
            ? proxy.$interceptorRegistrations()
            : List.copyOf(resolutionContext.getInterceptorRegistrations(
                Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(definition.getAnnotationMetadata())));
        resolutionContext.setBeanInterceptors(definition, candidates);
    }

    /**
     * Acquires and retains the complete candidate set of a runtime proxy.
     * @param resolutionContext The creation context
     * @param definition The proxy definition
     * @param methods The intercepted methods
     * @param <T> The bean type
     * @return The acquired registrations
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> List<BeanRegistration<Interceptor<T, ?>>> captureProxyCandidates(
        BeanResolutionContext resolutionContext, BeanDefinition<T> definition,
        Collection<ExecutableMethod<T, ?>> methods) {
        List<AnnotationMetadata> metadata = new ArrayList<>(methods.size() + 2);
        metadata.add(definition.getAnnotationMetadata());
        metadata.add(definition.getConstructor().getAnnotationMetadata());
        metadata.addAll(methods);
        List<BeanRegistration<Interceptor<T, ?>>> candidates = new ArrayList<>((Collection) resolutionContext.getInterceptorRegistrations(
            Interceptor.ARGUMENT,
            Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(metadata.toArray(AnnotationMetadata[]::new)))
        ));
        resolutionContext.setBeanInterceptors(definition, candidates);
        return candidates;
    }

    /**
     * Finds an existing registration for a target supplied to a proxy, including a swapped-in target.
     * This lookup neither creates a bean nor takes ownership of it.
     * @param beanLocator The context used by the proxy
     * @param bean The target, or null
     * @return The existing registration, or null when the locator does not hold it
     */
    @UsedByGeneratedCode
    public @Nullable BeanRegistration<?> findProxyTargetRegistration(BeanLocator beanLocator, @Nullable Object bean) {
        if (bean == null || !(beanLocator instanceof BeanDefinitionRegistry definitions)) {
            return null;
        }
        return definitions.findBeanRegistration(bean).orElse(null);
    }

    /**
     * Selects the interceptors of a proxy that fronts one target and keeps them itself.
     * @param beanLocator The context used for unmanaged targets
     * @param methods The intercepted methods
     * @param introduction Whether introduction advice is required
     * @param target The target registration, if known
     * @param bean The invocation target
     * @param proxyDependencies The dependency group of the proxy, which keeps the selection for a target that
     *                          cannot own interceptors itself
     * @return The interceptors selected for each method
     */
    @UsedByGeneratedCode
    public Interceptor<?, ?>[][] resolveTargetInterceptors(BeanLocator beanLocator,
        ExecutableMethod<?, ?>[] methods, boolean introduction,
        @Nullable BeanRegistration<?> target, @Nullable Object bean, @Nullable BeanDependencyGroup proxyDependencies) {
        return targetInterceptors(beanLocator, methods, introduction, proxyDependencies).select(target, bean);
    }

    /**
     * Creates the holder a proxy keeps the interceptors it selects for each of its targets in.
     *
     * @param beanLocator The context of the proxy
     * @param methods The intercepted methods
     * @param introduction Whether introduction advice is required
     * @param proxyDependencies The dependency group of the proxy, which owns the interceptors of a target that
     *                          cannot own any itself
     * @return The holder
     */
    @UsedByGeneratedCode
    public TargetInterceptors targetInterceptors(BeanLocator beanLocator, ExecutableMethod<?, ?>[] methods,
                                                 boolean introduction, @Nullable BeanDependencyGroup proxyDependencies) {
        return new TargetInterceptors(this, beanLocator, methods, introduction, proxyDependencies);
    }

    /**
     * Selects the interceptors of the given methods from those bound to them, resolved as the bean's own.
     *
     * @param methods The intercepted methods
     * @param introduction Whether introduction advice is required
     * @param resolutionContext The resolution context of the bean that owns the interceptors
     * @return The interceptors selected for each method
     */
    Interceptor<?, ?>[][] selectForMethods(ExecutableMethod<?, ?>[] methods, boolean introduction,
                                           BeanResolutionContext resolutionContext) {
        return selectForMethods(methods, introduction,
            resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, bindingOf(methods)));
    }

    /**
     * Selects the interceptors of the given methods for one call, when nothing can own them.
     *
     * @param beanLocator The context of the proxy
     * @param methods The intercepted methods
     * @param introduction Whether introduction advice is required
     * @return The interceptors selected for each method
     */
    Interceptor<?, ?>[][] selectUnowned(BeanLocator beanLocator, ExecutableMethod<?, ?>[] methods, boolean introduction) {
        return selectForMethods(methods, introduction, beanLocator instanceof BeanDefinitionRegistry definitions
            ? definitions.getBeanRegistrations(Interceptor.ARGUMENT, bindingOf(methods))
            : List.of());
    }

    private Qualifier<Interceptor<?, ?>> bindingOf(ExecutableMethod<?, ?>[] methods) {
        // each method keeps its own occurrences of a binding annotation that binds members
        AnnotationMetadata[] interceptionPoints = new AnnotationMetadata[methods.length];
        for (int i = 0; i < methods.length; i++) {
            interceptionPoints[i] = methods[i].getAnnotationMetadata();
        }
        return InterceptorBindingQualifier.ofInterceptionPoints(interceptionPoints);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Interceptor<?, ?>[][] selectForMethods(ExecutableMethod<?, ?>[] methods,
                                                          boolean introduction,
                                                          Collection<? extends BeanRegistration<?>> registrations) {
        List list = new ArrayList<>(registrations);
        Interceptor<?, ?>[][] selection = new Interceptor[methods.length][];
        for (int i = 0; i < methods.length; i++) {
            ExecutableMethod method = methods[i];
            selection[i] = selectMethodInterceptors(method, list,
                introduction ? InterceptorKind.INTRODUCTION : InterceptorKind.AROUND);
        }
        return selection;
    }

    /**
     * Acquires candidates for constructor-only discovery when no explicit set was supplied.
     * @param resolutionContext The resolution context
     * @param definition The bean definition
     * @param constructor The intercepted constructor
     * @param <T> The bean type
     * @return The acquired candidates
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Collection<BeanRegistration<Interceptor<T, T>>> resolveConstructorCandidates(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> definition,
        BeanConstructor<T> constructor) {
        AnnotationMetadataHierarchy hierarchy = new AnnotationMetadataHierarchy(definition.getAnnotationMetadata(), constructor.getAnnotationMetadata());
        Collection<AnnotationValue<?>> bindings = AbstractInterceptorChain.resolveInterceptorValues(hierarchy, InterceptorKind.AROUND_CONSTRUCT);
        return (Collection) resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, Qualifiers.byInterceptorBindingValues(bindings));
    }

    /**
     * Returns the authoritative retained lifecycle candidates, falling back to legacy discovery only when
     * the caller has no retained selection. An empty retained selection is authoritative.
     * @param resolutionContext The resolution context
     * @param definition The lifecycle owner
     * @param method The lifecycle method
     * @param bean The bean instance
     * @param kind The lifecycle kind
     * @return The candidates
     */
    public Collection<BeanRegistration<Interceptor<?, ?>>> resolveLifecycleCandidates(
        BeanResolutionContext resolutionContext, BeanDefinition<?> definition,
        ExecutableMethod<?, ?> method, Object bean, InterceptorKind kind) {
        for (LifecycleCandidateSource source : lifecycleSources) {
            Collection<BeanRegistration<Interceptor<?, ?>>> candidates =
                source.findLifecycleCandidates(resolutionContext, definition, method, bean, kind);
            if (candidates != null) {
                return candidates;
            }
        }
        return List.of();
    }
}
