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
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The interceptors of the methods of a generated proxy.
 *
 * <p>A proxy that is the bean, generated for {@code @Around} or {@code @Introduction}, resolves them once in its
 * constructor with {@link #resolve(BeanResolutionContext, ExecutableMethod[], boolean)}: the bean's own, through the
 * context creating it, a non-singleton among them being the instance created with the bean or created now as its
 * dependent.</p>
 *
 * <p>A proxy that fronts a separate target, generated for {@code @Around(proxyTarget = true)} and so every scoped
 * proxy and every advised bean a factory produces, keeps an instance of this class. It holds the singleton
 * interceptors bound to its methods only. The non-singleton interceptors of a target are the target's own: they were
 * created with the target, as dependents of its registration, when its construction or lifecycle was intercepted,
 * and they are destroyed with it. This class finds them there, by definition, and creates as a further dependent of
 * the target any that the target has not got yet, which is the case for an interceptor bound only for {@code AROUND}.
 * The selection for the methods is then kept on the target's registration, so that it lives exactly as long as the
 * target does and a proxy selects once per target.</p>
 *
 * <p>An interceptor of a custom scope belongs to that scope, which decides when the instance is replaced: the
 * selection keeps where it goes, and the instance is obtained from the scope for every call.</p>
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
public final class ProxyInterceptors {

    private final BeanContext beanContext;
    private final InterceptorRegistry interceptorRegistry;
    private final ExecutableMethod<?, ?>[] methods;
    private final boolean introduction;
    private final List<BeanRegistration<?>> singletons;
    private final Qualifier<Interceptor<?, ?>> binding;
    private final Interceptor<?, ?> @Nullable [][] fixed;
    // the interceptors of a custom scope bound to the methods, whose instances are obtained for every call
    private final Set<BeanDefinition<?>> scoped;
    // Weak, so that a proxy fronting a different target per thread or request retains none of them: a target the
    // proxy holds is held by the proxy, and a target swapped in is held by whoever handed it over.
    private volatile @Nullable WeakReference<BeanRegistration<?>> lastTarget;
    private volatile @Nullable WeakReference<Object> unownedTarget;
    private volatile @Nullable Selection unowned;

    /**
     * @param resolutionContext The resolution context the proxy is created in, through which the registry and the
     *                          singleton interceptors are resolved
     * @param methods           The intercepted methods of the target, in the proxy's order
     * @param introduction      Whether the methods are introduced rather than intercepted around
     */
    @UsedByGeneratedCode
    public ProxyInterceptors(BeanResolutionContext resolutionContext,
                             ExecutableMethod<?, ?>[] methods,
                             boolean introduction) {
        this.beanContext = resolutionContext.getContext();
        this.interceptorRegistry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);
        this.methods = methods;
        this.introduction = introduction;
        // the hierarchy reverses the array it is given, so it gets a copy
        this.binding = Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(methods.clone()));
        // the singletons bound to the methods are the proxy's to share, resolved as the interceptors of the proxy
        // being created; the non-singletons are each target's own
        this.singletons = methods.length == 0
            ? List.of()
            : new ArrayList<>(resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(methods.clone()), true)));
        boolean perTarget = false;
        Set<BeanDefinition<?>> scopedDefinitions = new HashSet<>(2);
        if (methods.length > 0) {
            for (BeanDefinition<Interceptor<?, ?>> definition : beanContext.getBeanDefinitions(Interceptor.ARGUMENT, binding)) {
                if (!definition.isSingleton()) {
                    // a non-singleton bound to the methods is each target's own, or its scope's, so the selection is
                    // per target; whether it applies to a method is the registry's call, made then
                    perTarget = true;
                    if (resolutionContext.isScopedInterceptor(definition)) {
                        scopedDefinitions.add(definition);
                    }
                }
            }
        }
        this.fixed = perTarget ? null : select(singletons);
        this.scoped = scopedDefinitions.isEmpty() ? Set.of() : scopedDefinitions;
    }

    /**
     * Resolves the interceptors of the methods of a proxy that is the bean, for its constructor: the bean's own,
     * through the context creating it, selected for each method through the registry.
     *
     * @param resolutionContext The resolution context the bean is created in
     * @param methods           The intercepted methods, in the proxy's order
     * @param introduction      Whether the proxy introduces methods; an abstract one is then implemented by its
     *                          introduction interceptors and every other one is intercepted around
     * @return The interceptors, by method
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @UsedByGeneratedCode
    public static Interceptor<?, ?>[][] resolve(BeanResolutionContext resolutionContext,
                                                ExecutableMethod<?, ?>[] methods,
                                                boolean introduction) {
        Interceptor<?, ?>[][] result = new Interceptor[methods.length][];
        if (methods.length == 0) {
            return result;
        }
        InterceptorRegistry interceptorRegistry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);
        // the hierarchy reverses the array it is given, so it gets a copy
        List<BeanRegistration<Interceptor<?, ?>>> registrations = new ArrayList<>(resolutionContext.getInterceptorRegistrations(
            Interceptor.ARGUMENT,
            Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(methods.clone()))
        ));
        for (int i = 0; i < methods.length; i++) {
            result[i] = introduction && methods[i].isAbstract()
                ? InterceptorChain.resolveIntroductionInterceptors(interceptorRegistry, (ExecutableMethod) methods[i], (List) registrations)
                : InterceptorChain.resolveAroundInterceptors(interceptorRegistry, (ExecutableMethod) methods[i], (List) registrations);
        }
        return result;
    }

    /**
     * The interceptors of every method that do not depend on the target: the whole selection when no non-singleton
     * is bound to the methods, and the singletons alone otherwise. A creator that asks per target for the rest gets
     * these.
     *
     * @return The interceptors, by method
     */
    public Interceptor<?, ?>[][] shared() {
        return fixed != null ? fixed : select(singletons);
    }

    /**
     * Whether an interceptor of a custom scope is bound to the methods, so that the interceptors of a call are
     * obtained for the call, the instance of that interceptor being its scope's at the time.
     *
     * @return Whether a selection may not be kept as it is
     */
    public boolean hasScopedInterceptors() {
        return !scoped.isEmpty();
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
        Selection selection = selectionFor(target);
        if (scoped.isEmpty()) {
            return selection.selected();
        }
        List<BeanRegistration<?>> current = current(selection, target);
        Interceptor<?, ?>[][] result = new Interceptor[methods.length][];
        for (int i = 0; i < methods.length; i++) {
            result[i] = select(i, current);
        }
        return result;
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
        if (fixed != null) {
            return fixed[index];
        }
        Selection selection = selectionFor(target);
        if (scoped.isEmpty()) {
            return selection.selected()[index];
        }
        // only the method called is selected again, with the instances its scopes hold now
        return select(index, current(selection, target));
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
            return get(index, last);
        }
        WeakReference<Object> unownedReference = unownedTarget;
        if (target == null || (unownedReference != null && unownedReference.get() == target)) {
            return get(index, (BeanRegistration<?>) null);
        }
        BeanRegistration<?> registration = beanContext.findBeanRegistration(target).orElse(null);
        if (registration == null) {
            // remembered, so that the calls that follow do not ask the scopes again for the same object
            unownedTarget = new WeakReference<>(target);
        }
        return get(index, registration);
    }

    /**
     * The selection for a target, made on the first call for it. It is kept on the target's registration, keyed by
     * this selector, so that nothing outlives the target: a map held here, however weak its keys, would retain the
     * interceptors of a target a scope forgot until its next expunge.
     */
    private Selection selectionFor(@Nullable BeanRegistration<?> target) {
        if (target == null || target.getBean() == null) {
            return unowned();
        }
        WeakReference<BeanRegistration<?>> last = lastTarget;
        if (last == null || last.get() != target) {
            lastTarget = new WeakReference<>(target);
        }
        try {
            // the singletons, and for each non-singleton the instance the target owns, or one created for it that
            // joins its dependents
            return target.dependentState(this, () -> selectionOf(new ArrayList<>(target.getInterceptorRegistrations(Interceptor.ARGUMENT, binding))));
        } catch (UnsupportedOperationException e) {
            // a registration a custom scope built by hand, which the context did not create and owns nothing through
            return unowned();
        }
    }

    /**
     * The selection for a target the context holds no registration for, made once, its non-singleton instances
     * never destroyed, as a proxy compiled before this class did for every target.
     */
    private Selection unowned() {
        Selection result = unowned;
        if (result == null) {
            synchronized (this) {
                result = unowned;
                if (result == null) {
                    result = selectionOf(new ArrayList<>(beanContext.getBeanRegistrations(Interceptor.ARGUMENT, binding)));
                    unowned = result;
                }
            }
        }
        return result;
    }

    private Selection selectionOf(List<BeanRegistration<?>> registrations) {
        // with an interceptor of a custom scope among them, every call selects again, so nothing is selected now
        return new Selection(registrations, scoped.isEmpty() ? select(registrations) : null);
    }

    /**
     * The registrations of a selection with the instance of each interceptor of a custom scope replaced by the one
     * its scope holds now: through the target, as its interceptor, or through the context for no target.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<BeanRegistration<?>> current(Selection selection, @Nullable BeanRegistration<?> target) {
        List<BeanRegistration<?>> registrations = selection.registrations();
        List<BeanRegistration<?>> current = new ArrayList<>(registrations.size());
        boolean owned = selection != unowned;
        for (BeanRegistration<?> registration : registrations) {
            BeanDefinition definition = registration.getBeanDefinition();
            if (scoped.contains(definition)) {
                current.add(owned && target != null ? target.getInterceptorRegistration(definition) : beanContext.getBeanRegistration(definition));
            } else {
                current.add(registration);
            }
        }
        return current;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Interceptor<?, ?>[] select(int index, List<BeanRegistration<?>> registrations) {
        return introduction
            ? InterceptorChain.resolveIntroductionInterceptors(interceptorRegistry, (ExecutableMethod) methods[index], (List) registrations)
            : InterceptorChain.resolveAroundInterceptors(interceptorRegistry, (ExecutableMethod) methods[index], (List) registrations);
    }

    private Interceptor<?, ?>[][] select(List<BeanRegistration<?>> registrations) {
        Interceptor<?, ?>[][] result = new Interceptor[methods.length][];
        for (int i = 0; i < methods.length; i++) {
            result[i] = select(i, registrations);
        }
        return result;
    }

    /**
     * The interceptors of the methods for one target.
     *
     * @param registrations The registrations of the interceptors bound to the methods
     * @param interceptors  The interceptors selected from them for each method, or {@code null} when an interceptor
     *                      of a custom scope is among them and every call selects again
     */
    private record Selection(List<BeanRegistration<?>> registrations, Interceptor<?, ?> @Nullable [][] interceptors) {

        Interceptor<?, ?>[][] selected() {
            return Objects.requireNonNull(interceptors);
        }
    }
}
