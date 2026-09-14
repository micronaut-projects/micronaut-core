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
package io.micronaut.inject.proxy;

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.ArrayList;

/**
 * The interceptor registrations of the target of a proxy that holds its target separately.
 *
 * <p>The list is what the target's bean registration retains as its interceptor registrations, so it lives exactly as
 * long as the target. That makes it the place to keep what a proxy derives from it: the interceptors of each of the
 * proxy's methods for this target, which a lazy proxy would otherwise have to select again on every call.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.1
 */
@Internal
public final class ProxyTargetInterceptorRegistrations extends ArrayList<BeanRegistration<?>> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean lifecycleResolved;
    private transient volatile @Nullable Object resolvedInterceptors;

    /**
     * @param initialCapacity   The initial capacity
     * @param lifecycleResolved Whether the list starts with the interceptors the target resolved for its own
     *                          construction or post-construct, see {@link #isLifecycleResolved()}
     */
    public ProxyTargetInterceptorRegistrations(int initialCapacity, boolean lifecycleResolved) {
        super(initialCapacity);
        this.lifecycleResolved = lifecycleResolved;
    }

    /**
     * Whether the list holds every interceptor the target's lifecycle interception selects from.
     *
     * <p>A target with constructor or post-construct advice resolved that set by all of its bindings, and the list
     * starts with it. A target with neither resolved nothing, so the list holds only the non-singleton interceptors
     * created for the proxy, and its pre-destroy interception must still find the singleton ones by binding.</p>
     *
     * @return {@code true} if the lifecycle set was resolved
     */
    public boolean isLifecycleResolved() {
        return lifecycleResolved;
    }

    /**
     * @return What a proxy stored with {@link #setResolvedInterceptors(Object)}, or {@code null}
     */
    public @Nullable Object getResolvedInterceptors() {
        return resolvedInterceptors;
    }

    /**
     * Stores the interceptors a proxy selected for this target.
     *
     * @param resolvedInterceptors The interceptors
     */
    public void setResolvedInterceptors(@Nullable Object resolvedInterceptors) {
        this.resolvedInterceptors = resolvedInterceptors;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
