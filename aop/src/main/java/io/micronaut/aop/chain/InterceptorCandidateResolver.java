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
import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.RegisteredBeanInterceptors;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Candidate discovery, including compatibility fallbacks for lifecycle calls without retained candidates. */
@Internal
@NullMarked
public final class InterceptorCandidateResolver {
    private final InterceptorRegistry registry;

    /**
     * @param registry The selection strategy used after candidate acquisition
     */
    public InterceptorCandidateResolver(InterceptorRegistry registry) {
        this.registry = registry;
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
    public <T> List<BeanRegistration<Interceptor<T, ?>>> resolveCandidates(
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
     * Reuses a target's cached selection or acquires candidates through its dependency owner.
     * @param beanLocator The context used for unmanaged targets
     * @param targetDefinition The target definition
     * @param methods The intercepted methods
     * @param introduction Whether introduction advice is required
     * @param target The target registration, if known
     * @param bean The invocation target
     * @return The interceptors selected for each method
     */
    public Interceptor<?, ?>[][] resolveTargetInterceptors(BeanLocator beanLocator,
        BeanDefinition<?> targetDefinition, ExecutableMethod<?, ?>[] methods, boolean introduction,
        @Nullable BeanRegistration<?> target, @Nullable Object bean) {
        if (target != null && target.getBean() == bean) {
            // read before a selector is created for it, on every call
            Interceptor<?, ?>[][] kept = RegisteredBeanInterceptors.kept(target, targetDefinition);
            if (kept != null) {
                return kept;
            }
            Interceptor<?, ?>[][] selection = RegisteredBeanInterceptors.select(target, targetDefinition, resolutionContext -> selectForMethods(
                methods,
                introduction,
                resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, bindingOf(methods))
            ));
            if (selection != null) {
                return selection;
            }
        }
        Interceptor<?, ?>[][] unowned = RegisteredBeanInterceptors.keptUnowned(beanLocator, targetDefinition);
        if (unowned != null) {
            return unowned;
        }
        return RegisteredBeanInterceptors.selectUnowned(beanLocator, targetDefinition, Interceptor.ARGUMENT, bindingOf(methods), registrations -> selectForMethods(
            methods,
            introduction,
            registrations
        ));
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
            selection[i] = registry.resolveMethodInterceptors(method, list,
                introduction ? InterceptorKind.INTRODUCTION : InterceptorKind.AROUND);
        }
        return selection;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    <T> Collection<BeanRegistration<Interceptor<T, T>>> resolveConstructorCandidates(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> definition,
        BeanConstructor<T> constructor) {
        AnnotationMetadataHierarchy hierarchy = new AnnotationMetadataHierarchy(definition.getAnnotationMetadata(), constructor.getAnnotationMetadata());
        Collection<AnnotationValue<?>> bindings = AbstractInterceptorChain.resolveInterceptorValues(hierarchy, InterceptorKind.AROUND_CONSTRUCT);
        return (Collection) resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, Qualifiers.byInterceptorBindingValues(bindings));
    }

    /**
     * Candidate discovery for older generated definitions and direct callers without retained lifecycle state.
     * @param resolutionContext The resolution context
     * @param definition The lifecycle owner
     * @param method The lifecycle method
     * @param bean The bean instance
     * @param kind The lifecycle kind
     * @return The legacy interceptor candidates
     * @since 5.3.0
     */
    @SuppressWarnings("unchecked") // Resolution contexts retain the legacy wildcard-list boundary.
    Collection<BeanRegistration<Interceptor<?, ?>>> resolveLifecycleCandidates(
        BeanResolutionContext resolutionContext,
        BeanDefinition<?> definition,
        ExecutableMethod<?, ?> method,
        Object bean,
        InterceptorKind kind) {
        if (bean instanceof Intercepted intercepted && !intercepted.$interceptorRegistrations().isEmpty()) {
            return intercepted.$interceptorRegistrations();
        }
        if (kind == InterceptorKind.PRE_DESTROY) {
            List<?> retained = resolutionContext.getBeanDestructionInterceptors(definition);
            if (retained != null) {
                return (List<BeanRegistration<Interceptor<?, ?>>>) retained;
            }
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
    private Collection<BeanRegistration<Interceptor<?, ?>>> resolveLifecycleInterceptors(
        BeanResolutionContext resolutionContext,
        Collection<AnnotationValue<?>> binding) {

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
     * the exact selected set through {@link BeanResolutionContext#getBeanDestructionInterceptors(BeanDefinition)}; this fallback
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
