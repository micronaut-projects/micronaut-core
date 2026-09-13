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
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.DependentBeanProvider;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.DelegatingBeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Selects the interceptors of the methods of a proxy that fronts a separate target, for the target of each call.
 *
 * <p>A proxy generated for {@code @Around(proxyTarget = true)}, and so every scoped proxy and every advised bean a
 * factory produces, is injected with the singleton interceptors bound to its methods only. The non-singleton
 * interceptors of a target are the target's own: they were created with the target, as dependents of its
 * registration, when its construction or lifecycle was intercepted, and they are destroyed with it. This class finds
 * them there, by definition, and creates as a further dependent of the target any that the target has not got yet,
 * which is the case for an interceptor bound only for {@code AROUND}. The selection for the methods is then kept
 * against the target's registration, so that every call through any proxy of the same class fronting that target
 * selects once.</p>
 *
 * <p>When no non-singleton interceptor is bound to any of the methods, which is the common case, the selection is
 * made once with the singletons and every target gets it, and no registration is consulted.</p>
 *
 * <p>A target the context holds no registration for, such as an object handed to {@code swap} that the context did
 * not create, is intercepted with instances this class creates once and destroys with nothing, as a proxy compiled
 * before this class did for every target.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@UsedByGeneratedCode
public final class ProxyTargetInterceptors {

    private final BeanContext beanContext;
    private final InterceptorRegistry interceptorRegistry;
    private final Class<?> proxyClass;
    private final ExecutableMethod<?, ?>[] methods;
    private final boolean introduction;
    private final List<BeanRegistration<?>> singletons;
    private final List<BeanDefinition<Interceptor<?, ?>>> nonSingletons;
    private final Interceptor<?, ?> @Nullable [][] fixed;
    private volatile @Nullable BeanRegistration<?> lastTarget;
    private volatile @Nullable Object unownedTarget;
    private volatile Interceptor<?, ?> @Nullable [][] unowned;

    /**
     * @param beanContext         The bean context
     * @param interceptorRegistry The interceptor registry
     * @param proxyClass          The generated proxy class, the key of the selection kept on each target
     * @param methods             The intercepted methods of the target, in the proxy's order
     * @param registrations       The registrations the proxy was injected with, singletons only
     * @param introduction        Whether the methods are introduced rather than intercepted around
     */
    @UsedByGeneratedCode
    public ProxyTargetInterceptors(BeanContext beanContext,
                                   InterceptorRegistry interceptorRegistry,
                                   Class<?> proxyClass,
                                   ExecutableMethod<?, ?>[] methods,
                                   List<? extends BeanRegistration<?>> registrations,
                                   boolean introduction) {
        this.beanContext = beanContext;
        this.interceptorRegistry = interceptorRegistry;
        this.proxyClass = proxyClass;
        this.methods = methods;
        this.introduction = introduction;
        this.singletons = new ArrayList<>(registrations);
        List<BeanDefinition<Interceptor<?, ?>>> found = List.of();
        if (methods.length > 0) {
            // the hierarchy reverses the array it is given, so it gets a copy
            Qualifier<Interceptor<?, ?>> binding = Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(methods.clone()));
            for (BeanDefinition<Interceptor<?, ?>> definition : beanContext.getBeanDefinitions(Interceptor.ARGUMENT, binding)) {
                if (!definition.isSingleton() && bindsMethods(definition)) {
                    if (found.isEmpty()) {
                        found = new ArrayList<>(2);
                    }
                    if (!found.contains(definition)) {
                        found.add(definition);
                    }
                }
            }
        }
        this.nonSingletons = found;
        this.fixed = found.isEmpty() ? select(singletons) : null;
    }

    /**
     * The interceptors of every method for the given target.
     *
     * @param target The registration of the target, or {@code null} when the context holds none
     * @return The interceptors, by method
     */
    @UsedByGeneratedCode
    public Interceptor<?, ?>[][] resolve(@Nullable BeanRegistration<?> target) {
        if (fixed != null) {
            return fixed;
        }
        if (target == null || target.getBean() == null || !(target instanceof DependentBeanProvider provider)) {
            return unowned();
        }
        lastTarget = target;
        return provider.dependentState(proxyClass, () -> select(ownedBy(provider)));
    }

    /**
     * The interceptors of one method for the given target.
     *
     * @param index  The index of the method
     * @param target The registration of the target, or {@code null} when the context holds none
     * @return The interceptors
     */
    @UsedByGeneratedCode
    public Interceptor<?, ?>[] get(int index, @Nullable BeanRegistration<?> target) {
        return resolve(target)[index];
    }

    /**
     * The interceptors of one method for the given target, found by its registration.
     *
     * <p>For a proxy whose target can change hands, such as a hot-swappable one: the registration resolved last is
     * tried first, then the context is asked for the registration of the target.</p>
     *
     * @param index  The index of the method
     * @param target The target of the call
     * @return The interceptors
     */
    @UsedByGeneratedCode
    public Interceptor<?, ?>[] get(int index, @Nullable Object target) {
        if (fixed != null) {
            return fixed[index];
        }
        BeanRegistration<?> last = lastTarget;
        if (last != null && last.getBean() == target && last instanceof DependentBeanProvider provider) {
            return provider.dependentState(proxyClass, () -> select(ownedBy(provider)))[index];
        }
        if (target == null || target == unownedTarget) {
            return unowned()[index];
        }
        BeanRegistration<?> registration = beanContext.findBeanRegistration(target).orElse(null);
        if (registration == null) {
            // remembered, so that the calls that follow do not ask the scopes again for the same object
            unownedTarget = target;
        }
        return resolve(registration)[index];
    }

    /**
     * The singletons and the target's own instances of the non-singleton interceptors, creating as a dependent of the
     * target any it has not got yet.
     */
    private List<BeanRegistration<?>> ownedBy(DependentBeanProvider target) {
        List<BeanRegistration<?>> dependents = target.dependentBeans();
        List<BeanRegistration<?>> registrations = new ArrayList<>(singletons.size() + nonSingletons.size());
        registrations.addAll(singletons);
        for (BeanDefinition<Interceptor<?, ?>> definition : nonSingletons) {
            BeanRegistration<?> registration = find(dependents, definition);
            if (registration == null) {
                registration = beanContext.getBeanRegistration(definition);
                if (isDependent(definition)) {
                    target.addDependentBean(registration);
                }
            }
            registrations.add(registration);
        }
        return registrations;
    }

    /**
     * Instances for a target the context holds no registration for, created once and never destroyed, as a proxy
     * compiled before this class did for every target.
     */
    private Interceptor<?, ?>[][] unowned() {
        Interceptor<?, ?>[][] result = unowned;
        if (result == null) {
            synchronized (this) {
                result = unowned;
                if (result == null) {
                    List<BeanRegistration<?>> registrations = new ArrayList<>(singletons.size() + nonSingletons.size());
                    registrations.addAll(singletons);
                    for (BeanDefinition<Interceptor<?, ?>> definition : nonSingletons) {
                        registrations.add(beanContext.getBeanRegistration(definition));
                    }
                    result = select(registrations);
                    unowned = result;
                }
            }
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Interceptor<?, ?>[][] select(List<BeanRegistration<?>> registrations) {
        Interceptor<?, ?>[][] result = new Interceptor[methods.length][];
        for (int i = 0; i < methods.length; i++) {
            result[i] = introduction
                ? InterceptorChain.resolveIntroductionInterceptors(interceptorRegistry, (ExecutableMethod) methods[i], (List) registrations)
                : InterceptorChain.resolveAroundInterceptors(interceptorRegistry, (ExecutableMethod) methods[i], (List) registrations);
        }
        return result;
    }

    /**
     * Whether the interceptor is bound for the interception of methods at all. One bound only for a lifecycle kind
     * is resolved by the lifecycle interception of the target itself, and the proxy has no reason to create it.
     */
    private static boolean bindsMethods(BeanDefinition<?> definition) {
        List<AnnotationValue<Annotation>> bindings = definition.getAnnotationMetadata().getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING);
        if (bindings.isEmpty()) {
            return true;
        }
        for (AnnotationValue<Annotation> binding : bindings) {
            InterceptorKind kind = binding.enumValue("kind", InterceptorKind.class).orElse(InterceptorKind.AROUND);
            if (kind == InterceptorKind.AROUND || kind == InterceptorKind.INTRODUCTION) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an interceptor of this definition belongs to the bean it is created for: a prototype, or a bean with no
     * scope. A bean of a custom scope belongs to its scope and is not attached to a target.
     */
    private static boolean isDependent(BeanDefinition<?> definition) {
        String scope = definition.getAnnotationMetadata().getAnnotationNameByStereotype(AnnotationUtil.SCOPE).orElse(null);
        return scope == null || Prototype.class.getName().equals(scope);
    }

    @Nullable
    private static BeanRegistration<?> find(List<BeanRegistration<?>> registrations, BeanDefinition<?> definition) {
        BeanDefinition<?> unwrapped = unwrap(definition);
        for (BeanRegistration<?> registration : registrations) {
            if (registration.getBean() instanceof Interceptor && unwrap(registration.getBeanDefinition()).equals(unwrapped)) {
                return registration;
            }
        }
        return null;
    }

    private static BeanDefinition<?> unwrap(BeanDefinition<?> definition) {
        BeanDefinition<?> unwrapped = definition;
        while (unwrapped instanceof DelegatingBeanDefinition<?> delegating) {
            unwrapped = delegating.getTarget();
        }
        return unwrapped;
    }
}
