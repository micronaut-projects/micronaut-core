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

import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
/**
 * Selects the interceptors of the methods of a proxy that fronts a separate target, for the target of each call.
 *
 * <p>A proxy generated for {@code @Around(proxyTarget = true)}, and so every scoped proxy and every advised bean a
 * factory produces, holds the singleton interceptors bound to its methods only, resolved here. The non-singleton
 * interceptors of a target are the target's own: they were created with the target, as dependents of its
 * registration, when its construction or lifecycle was intercepted, and they are destroyed with it. This class finds
 * them there, by definition, and creates as a further dependent of the target any that the target has not got yet,
 * which is the case for an interceptor bound only for {@code AROUND}. The selection for the methods is then kept
 * on the target's registration, so that it lives exactly as long as the target does and a proxy selects once per
 * target.</p>
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
    private final ExecutableMethod<?, ?>[] methods;
    private final boolean introduction;
    private final List<BeanRegistration<?>> singletons;
    private final Qualifier<Interceptor<?, ?>> binding;
    private final List<BeanDefinition<Interceptor<?, ?>>> nonSingletons;
    private final Interceptor<?, ?> @Nullable [][] fixed;
    // Weak, so that a proxy fronting a different target per thread or request retains none of them: a target the
    // proxy holds is held by the proxy, and a target swapped in is held by whoever handed it over.
    private volatile @Nullable WeakReference<BeanRegistration<?>> lastTarget;
    private volatile @Nullable WeakReference<Object> unownedTarget;
    private volatile Interceptor<?, ?> @Nullable [][] unowned;

    /**
     * @param resolutionContext The resolution context the proxy is created in, through which the registry and the
     *                          singleton interceptors are resolved
     * @param methods           The intercepted methods of the target, in the proxy's order
     * @param introduction      Whether the methods are introduced rather than intercepted around
     */
    @UsedByGeneratedCode
    public ProxyTargetInterceptors(BeanResolutionContext resolutionContext,
                                   ExecutableMethod<?, ?>[] methods,
                                   boolean introduction) {
        this.beanContext = resolutionContext.getContext();
        this.interceptorRegistry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);
        this.methods = methods;
        this.introduction = introduction;
        // the hierarchy reverses the array it is given, so it gets a copy
        this.binding = Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(methods.clone()));
        // the singletons bound to the methods are the proxy's to share; the non-singletons are each target's own
        this.singletons = methods.length == 0
            ? List.of()
            : new ArrayList<>(beanContext.getBeanRegistrations(Interceptor.ARGUMENT, ((InterceptorBindingQualifier<Interceptor<?, ?>>) binding).singletonsOnly()));
        List<BeanDefinition<Interceptor<?, ?>>> found = List.of();
        if (methods.length > 0) {
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
     * Whether any interceptor is bound to the method at the index, for any target: a method a runtime proxy must
     * override. When a non-singleton interceptor is bound to the methods, this answers by the definitions bound to
     * the method, without selecting for a target; the selection for a target may still leave the method alone.
     *
     * @param index The index of the method
     * @return Whether an interceptor may apply to the method
     */
    public boolean intercepted(int index) {
        if (fixed != null) {
            return fixed[index].length > 0;
        }
        return !beanContext.getBeanDefinitions(Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(methods[index].getAnnotationMetadata())).isEmpty();
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
        if (target == null || target.getBean() == null) {
            return unowned();
        }
        lastTarget = new WeakReference<>(target);
        return selectionFor(target);
    }

    /**
     * The selection kept for a target, made on the first call for it. It is kept on the target's registration,
     * keyed by this selector, so that nothing outlives the target: a map held here, however weak its keys, would
     * retain the interceptors of a target a scope forgot until its next expunge.
     */
    private Interceptor<?, ?>[][] selectionFor(BeanRegistration<?> target) {
        return target.dependentState(this, () -> select(ownedBy(target)));
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
        WeakReference<BeanRegistration<?>> lastReference = lastTarget;
        BeanRegistration<?> last = lastReference == null ? null : lastReference.get();
        if (last != null && last.getBean() == target) {
            return selectionFor(last)[index];
        }
        WeakReference<Object> unownedReference = unownedTarget;
        if (target == null || (unownedReference != null && unownedReference.get() == target)) {
            return unowned()[index];
        }
        BeanRegistration<?> registration = beanContext.findBeanRegistration(target).orElse(null);
        if (registration == null) {
            // remembered, so that the calls that follow do not ask the scopes again for the same object
            unownedTarget = new WeakReference<>(target);
        }
        return resolve(registration)[index];
    }

    /**
     * The interceptors of the methods for a target, the target's own: the singletons, and for each non-singleton the
     * instance the target owns, or one created for it that joins its dependents.
     */
    private List<BeanRegistration<?>> ownedBy(BeanRegistration<?> target) {
        try {
            return new ArrayList<>(target.getInterceptorRegistrations(Interceptor.ARGUMENT, binding));
        } catch (UnsupportedOperationException e) {
            // a registration a custom scope built by hand, which the context did not create and owns nothing through
            return resolveUnowned();
        }
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
                    result = select(resolveUnowned());
                    unowned = result;
                }
            }
        }
        return result;
    }

    /**
     * Resolves the interceptors of the methods with no dependent scope to reuse from, so every non-singleton one is
     * a new instance that belongs to nothing.
     */
    private List<BeanRegistration<?>> resolveUnowned() {
        return new ArrayList<>(beanContext.getBeanRegistrations(Interceptor.ARGUMENT, binding));
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
}
