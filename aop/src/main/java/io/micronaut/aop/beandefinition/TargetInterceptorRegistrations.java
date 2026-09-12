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
package io.micronaut.aop.beandefinition;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;

/**
 * The interceptor registrations resolved for one bean, holding the interceptors they select for each method of the
 * proxy that fronts the bean.
 *
 * <p>The scenario this exists for is a proxy whose target is not a singleton, such as a {@code @RequestScope} or
 * {@code @ThreadLocal} bean, which fronts a different target from one call to the next:</p>
 *
 * <pre>{@code
 * @RequestScope @Counted
 * class Ledger { void record() {} }
 * }</pre>
 *
 * <p>The interceptors bound to such a target are resolved while the target is created and belong to it, so the
 * proxy selects the interceptors of a method from the target's registrations rather than from a set it resolved
 * once for itself. That is what makes a non-singleton interceptor one instance per target, shared by the target's
 * post-construct, its methods and its pre-destroy, and destroyed with it. Selection filters and orders the
 * registrations by the method's bindings, which is too much to repeat on every call, so the outcome is kept here,
 * by method, for the life of the target.</p>
 *
 * <p>The list is the one the bean's {@link BeanRegistration} carries and its pre-destroy interception reads,
 * unchanged, and it is read-only. Selections are indexed by the method's position in the proxy and checked against
 * the method itself, so a proxy of another shape over the same target selects afresh instead of reading an entry
 * that is not its own.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.2
 */
@Internal
public final class TargetInterceptorRegistrations extends AbstractList<BeanRegistration<Interceptor<?, ?>>> implements RandomAccess {

    private static final Selection[] NONE = new Selection[0];

    private final List<BeanRegistration<Interceptor<?, ?>>> registrations;
    /**
     * Replaced rather than mutated, so that a read needs no lock.
     */
    private volatile Selection[] selections = NONE;

    private TargetInterceptorRegistrations(List<BeanRegistration<Interceptor<?, ?>>> registrations) {
        this.registrations = registrations;
    }

    /**
     * Wraps the registrations resolved for a bean, unless they are wrapped already.
     *
     * @param registrations The registrations
     * @return The registrations, able to hold the selections made from them
     */
    @SuppressWarnings("unchecked")
    static List<?> of(List<?> registrations) {
        if (registrations instanceof TargetInterceptorRegistrations) {
            return registrations;
        }
        return new TargetInterceptorRegistrations((List<BeanRegistration<Interceptor<?, ?>>>) registrations);
    }

    /**
     * Selects the interceptors a proxy applies to one method of the target it fronts.
     *
     * <p>For a singleton target the interceptors the proxy selected for itself are returned: proxy and target are
     * one to one, and a singleton target has always been intercepted by the proxy's own set, its own registrations
     * serving its lifecycle phases. For any other target the interceptors are selected from the registrations
     * resolved for that target while it was created, so that a non-singleton interceptor is the instance which
     * also ran the target's post-construct, will run its pre-destroy, and is destroyed with it. A target that
     * carries no registrations, because it was created some other way than by its definition or its scope obtained
     * it some other way than through the context, is intercepted by the proxy's own set as before.</p>
     *
     * @param registry          The interceptor registry
     * @param target            The registration of the target of this call, or {@code null} when the proxy holds
     *                          none for it
     * @param method            The intercepted method, as the proxy holds it
     * @param index             The index of the method in the proxy
     * @param proxyInterceptors The interceptors the proxy selected for the method from its own registrations
     * @param <T>               The target type
     * @return The interceptors to apply to this call
     * @since 5.2.2
     */
    @UsedByGeneratedCode
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Interceptor<T, ?>[] select(InterceptorRegistry registry,
                                                 @Nullable BeanRegistration<T> target,
                                                 ExecutableMethod<T, ?> method,
                                                 int index,
                                                 Interceptor<T, ?>[] proxyInterceptors) {
        if (target == null || target.getBeanDefinition().isSingleton()) {
            return proxyInterceptors;
        }
        List<?> registrations = target.getInterceptorRegistrations();
        if (registrations instanceof TargetInterceptorRegistrations resolved) {
            return resolved.aroundInterceptors(registry, method, index);
        }
        if (registrations == null || registrations.isEmpty()) {
            return proxyInterceptors;
        }
        // Stored by another route than SharedInterceptorRegistrations#store, so there is nowhere to keep the selection
        return registry.resolveInterceptors(method, (Collection) registrations, InterceptorKind.AROUND);
    }

    @SuppressWarnings("unchecked")
    private <T> Interceptor<T, ?>[] aroundInterceptors(InterceptorRegistry registry, ExecutableMethod<T, ?> method, int index) {
        Selection[] current = selections;
        if (index < current.length) {
            Selection selection = current[index];
            if (selection != null && selection.method == method) {
                return (Interceptor<T, ?>[]) selection.interceptors;
            }
        }
        return select(registry, method, index);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private synchronized <T> Interceptor<T, ?>[] select(InterceptorRegistry registry, ExecutableMethod<T, ?> method, int index) {
        Selection[] current = selections;
        if (index < current.length) {
            Selection selection = current[index];
            if (selection != null && selection.method == method) {
                return (Interceptor<T, ?>[]) selection.interceptors;
            }
        }
        Interceptor<T, ?>[] interceptors = registry.resolveInterceptors(method, (Collection) registrations, InterceptorKind.AROUND);
        Selection[] replaced = Arrays.copyOf(current, Math.max(current.length, index + 1));
        replaced[index] = new Selection(method, interceptors);
        selections = replaced;
        return interceptors;
    }

    @Override
    public BeanRegistration<Interceptor<?, ?>> get(int index) {
        return registrations.get(index);
    }

    @Override
    public int size() {
        return registrations.size();
    }

    /**
     * The interceptors selected for one method, with the method they were selected for.
     *
     * @param method       The method
     * @param interceptors The interceptors selected for it
     */
    private record Selection(ExecutableMethod<?, ?> method, Interceptor<?, ?>[] interceptors) {
    }
}
