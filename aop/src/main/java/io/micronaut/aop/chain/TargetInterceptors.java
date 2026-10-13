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
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDependencyGroup;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.BeanDependencies;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The interceptors one proxy selected for each target it fronts. A proxy that selects for its target keeps one of
 * these instead of an array of its own, because it can front many targets: a scoped proxy sees a different one
 * for every scope instance, a hot-swappable one sees each target swapped in.
 *
 * <p>Three objects take part:</p>
 * <ul>
 *     <li>the <b>proxy</b>, which has exactly one of these, in a field, for as long as it lives;</li>
 *     <li>a <b>target</b>, the real bean instance a call is delegated to, and its
 *     {@linkplain BeanRegistration#dependencies() dependencies}: the record of what was created for that
 *     one instance and is destroyed with it;</li>
 *     <li>the <b>interceptors</b> of a target, one array for each proxied method.</li>
 * </ul>
 *
 * <p>What happens to them:</p>
 * <ol>
 *     <li>On the first call for a target its interceptors are selected. The unscoped ones are created as
 *     dependents of the target, not of the proxy, so that they are the instances the lifecycle of the target was
 *     intercepted with and are destroyed with it.</li>
 *     <li>The selection is kept here, with the proxy, and returned for every later call for that target.</li>
 *     <li>Together with the interceptors, the target is given one more dependent, a {@link Removal}. When the
 *     target is destroyed, so is the removal, which takes the selection of that target out of this proxy.</li>
 *     <li>A target that cannot own interceptors, because nothing would destroy them with it, is intercepted with
 *     interceptors the proxy owns. They are selected once and destroyed with the proxy.</li>
 * </ol>
 *
 * @since 5.3.0
 */
@Internal
public final class TargetInterceptors {
    /** The identifier and the definition every removal is registered under; see {@link RemovalDefinition}. */
    private static final BeanIdentifier REMOVAL_ID = BeanIdentifier.of(Removal.class.getName());
    private static final RemovalDefinition REMOVAL_DEFINITION = new RemovalDefinition();

    /** Selects, from the interceptors bound to the proxied methods, those that apply to each method. */
    private final InterceptorCandidateResolver resolver;
    /** The context of the proxy. */
    private final BeanLocator beanLocator;
    /** The proxied methods; a selection has one array of interceptors for each, in this order. */
    private final ExecutableMethod<?, ?>[] methods;
    /** Whether the proxy is an introduction, which runs around advice before introduction advice. */
    private final boolean introduction;
    /** The dependencies of the proxy itself, which own the interceptors used for a target that cannot own any. */
    private final @Nullable BeanDependencyGroup proxyDependencies;
    /**
     * The selection of each target. Guarded by its own monitor.
     *
     * <p>The key is the dependencies of the target and not the target instance: there is one such object for each
     * instance, it is compared by identity whatever the target's {@code equals} does, and it is what the removal
     * has at hand when the target is destroyed.</p>
     *
     * <p>Nothing in the map keeps a target alive. A scope may drop a target without destroying it, as the thread
     * local scope does when its thread ends; no removal runs then, and a strong entry would stay for the life of
     * the proxy, along with interceptors that may refer to the target. So the key is held weakly, and so is the
     * selection. What keeps a selection alive is the {@link Removal} given to the target, for exactly as long
     * as the target is.</p>
     */
    private final Map<BeanDependencies, WeakReference<Selected>> byTarget = new WeakHashMap<>();
    /** The interceptors the proxy owns, for targets that cannot own any. Selected on first use. */
    @SuppressWarnings("java:S3077") // the array is never written after it is published, only the reference is
    private volatile Interceptor<?, ?> @Nullable [][] ofProxy;

    TargetInterceptors(InterceptorCandidateResolver resolver, BeanLocator beanLocator,
                       ExecutableMethod<?, ?>[] methods, boolean introduction,
                       @Nullable BeanDependencyGroup proxyDependencies) {
        this.resolver = resolver;
        this.beanLocator = beanLocator;
        this.methods = methods;
        this.introduction = introduction;
        this.proxyDependencies = proxyDependencies;
    }

    /**
     * Returns the interceptors of a target, selecting them on the first call for it. A generated proxy calls this
     * on every intercepted call, with the target it is about to delegate to.
     *
     * @param targetRegistration The registration of the target bean, not of the proxy; null if the context holds
     *                           none for the instance
     * @param targetBean The target instance the proxy is calling
     * @param <T>        The type of the intercepted bean
     * @param <R>        The result type of the intercepted methods
     * @return The interceptors selected for each proxied method
     */
    @UsedByGeneratedCode
    @SuppressWarnings("unchecked")
    public <T, R> Interceptor<T, R>[][] resolve(@Nullable BeanRegistration<?> targetRegistration, @Nullable Object targetBean) {
        BeanDependencies targetDependencies = dependenciesOf(targetRegistration, targetBean);
        if (targetDependencies != null) {
            // The target can own interceptors: use its selection, making it on the first call.
            Selected selected = selectedOf(targetDependencies);
            Interceptor<?, ?>[][] interceptors = selected.interceptors;
            if (interceptors == null) {
                // First call for this target. The entry is locked, not the map, so that two threads making the
                // first call create the interceptors once while calls for other targets go on.
                synchronized (selected) {
                    interceptors = selected.interceptors;
                    if (interceptors == null) {
                        interceptors = select(targetDependencies, targetRegistration, selected);
                        // Stays null when the target is being destroyed; the next call asks again.
                        selected.interceptors = interceptors;
                    }
                }
            }
            if (interceptors != null) {
                // The interceptors of a method are of the bean the proxy fronts, which the caller knows the type of
                return (Interceptor<T, R>[][]) interceptors;
            }
            // The target is being destroyed and can no longer be given dependents: fall through.
        }
        return (Interceptor<T, R>[][]) ofProxy();
    }

    /**
     * Selects the interceptors of a target without keeping them here. This is for a proxy that fronts one target
     * for its whole life: it stores the result in a field of its own, so there is nothing to look up later and
     * nothing to remove when the target is destroyed.
     *
     * @param targetRegistration The registration of the target bean, not of the proxy; null if the context holds
     *                           none for the instance
     * @param targetBean The target instance of the proxy
     * @return The interceptors selected for each proxied method
     */
    Interceptor<?, ?>[][] select(@Nullable BeanRegistration<?> targetRegistration, @Nullable Object targetBean) {
        BeanDependencies targetDependencies = dependenciesOf(targetRegistration, targetBean);
        Interceptor<?, ?>[][] interceptors = targetDependencies == null ? null : select(targetDependencies, targetRegistration, null);
        return interceptors != null ? interceptors : ofProxy();
    }

    /**
     * Finds what interceptors created for the target would be destroyed with.
     *
     * @return The dependencies of the target, or null when the target cannot own interceptors: the context
     * has no registration for it, the registration is of another instance, or the registration was not created
     * by the container, as the one a custom scope builds for a bean it stores itself
     */
    private static @Nullable BeanDependencies dependenciesOf(@Nullable BeanRegistration<?> targetRegistration, @Nullable Object targetBean) {
        return targetRegistration != null && targetBean != null && targetRegistration.getBean() == targetBean
            ? targetRegistration.dependencies() : null;
    }

    /**
     * Selects the interceptors of a target, creating the unscoped ones as its dependents.
     *
     * @param targetDependencies The dependencies of the target
     * @param targetRegistration The registration of the target bean
     * @param kept The entry the result will be kept in, or null when the caller keeps the result itself
     * @return The interceptors, or null when the target is being destroyed
     */
    private Interceptor<?, ?> @Nullable [][] select(BeanDependencies targetDependencies, @Nullable BeanRegistration<?> targetRegistration,
                                                 @Nullable Selected kept) {
        BeanDefinition<?> targetDefinition = targetRegistration == null ? null : targetRegistration.getBeanDefinition();
        // Everything created or added inside the operation becomes a dependent of the target.
        return targetDependencies.resolveDependencies(beanLocator, targetDefinition, resolutionContext -> {
            // An unscoped interceptor already created for the target, to intercept its construction, is reused
            // here; the others are created now.
            Interceptor<?, ?>[][] interceptors = resolver.selectForMethods(methods, introduction, resolutionContext);
            if (kept != null) {
                // One more dependent of the target, which is not a real bean: destroying it removes the entry
                // from this proxy. Until then it is what refers to the entry strongly.
                resolutionContext.addDependentBean(new BeanRegistration<>(
                    REMOVAL_ID, REMOVAL_DEFINITION, new Removal(this, targetDependencies, kept)));
            }
            return interceptors;
        });
    }

    /**
     * @return The entry of the target, new and empty if the target has none or its entry was collected
     */
    private Selected selectedOf(BeanDependencies targetDependencies) {
        synchronized (byTarget) {
            WeakReference<Selected> reference = byTarget.get(targetDependencies);
            Selected selected = reference == null ? null : reference.get();
            if (selected == null) {
                // Until interceptors are selected into it, only the calling threads refer to the entry.
                selected = new Selected();
                byTarget.put(targetDependencies, new WeakReference<>(selected));
            }
            return selected;
        }
    }

    /** Forgets the selection of a destroyed target. Called by its {@link Removal}. */
    private void remove(BeanDependencies targetDependencies) {
        synchronized (byTarget) {
            byTarget.remove(targetDependencies);
        }
    }

    /**
     * Returns the interceptors used for a target that cannot own any. They are selected once and belong to the
     * proxy: created as its dependents, shared by all such targets and destroyed with it.
     */
    private Interceptor<?, ?>[][] ofProxy() {
        Interceptor<?, ?>[][] interceptors = ofProxy;
        if (interceptors != null) {
            return interceptors;
        }
        if (proxyDependencies instanceof BeanDependencies ofProxyBean) {
            synchronized (this) {
                interceptors = ofProxy;
                if (interceptors == null) {
                    // Null when the proxy itself is being destroyed.
                    interceptors = ofProxyBean.resolveDependencies(beanLocator, null,
                        resolutionContext -> resolver.selectForMethods(methods, introduction, resolutionContext));
                    ofProxy = interceptors;
                }
            }
            if (interceptors != null) {
                return interceptors;
            }
        }
        // Neither the target nor the proxy can own interceptors, so nothing is created or kept: the call is
        // intercepted with the interceptors the context already has.
        return resolver.selectUnowned(beanLocator, methods, introduction);
    }

    /**
     * The entry of one target: its interceptors, once selected. Its monitor serializes the first calls for the
     * target.
     */
    private static final class Selected {
        /** Null until selected; read without the monitor by later calls. */
        @SuppressWarnings("java:S3077") // the array is never written after it is published, only the reference is
        volatile Interceptor<?, ?> @Nullable [][] interceptors;
    }

    /**
     * A dependent of a target that exists to be destroyed with it. It is registered with the target when the
     * interceptors of the target are selected, and its destruction removes them from the proxy.
     *
     * @param from The selections of the proxy
     * @param targetDependencies The dependencies of the target, the key of the entry
     * @param kept The entry, referred to here so that it lives as long as the target
     */
    private record Removal(TargetInterceptors from, BeanDependencies targetDependencies, Selected kept) {
    }

    /**
     * The definition a {@link Removal} is destroyed through: the container destroys a dependent by calling
     * {@code dispose} on its definition. It is never registered with a context, so a removal cannot be looked up
     * or injected.
     */
    private static final class RemovalDefinition implements DisposableBeanDefinition<Removal> {
        @Override
        public Class<Removal> getBeanType() {
            return Removal.class;
        }

        @Override
        public boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
            return true;
        }

        @Override
        public Removal dispose(BeanResolutionContext resolutionContext, BeanContext context, Removal bean) {
            // Runs when the target is destroyed.
            bean.from.remove(bean.targetDependencies);
            return bean;
        }
    }
}
