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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.ArgumentUtils;

/**
 * Static registry for the provider used by {@link BeanIntrospector} to discover introspections.
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class BeanIntrospectionProviders {

    private static final BeanIntrospectionsProvider DEFAULT_PROVIDER = new DefaultBeanIntrospectionsProvider();
    private static volatile BeanIntrospectionsProvider provider = DEFAULT_PROVIDER;

    private BeanIntrospectionProviders() {
    }

    /**
     * Returns the current bean introspections provider.
     *
     * @return The current bean introspections provider
     */
    public static BeanIntrospectionsProvider get() {
        return provider;
    }

    /**
     * Override the default {@link BeanIntrospectionsProvider}.
     *
     * @param beanIntrospectionsProvider The bean introspections provider to use
     * @return The previously registered provider
     */
    public static BeanIntrospectionsProvider set(BeanIntrospectionsProvider beanIntrospectionsProvider) {
        ArgumentUtils.requireNonNull("beanIntrospectionsProvider", beanIntrospectionsProvider);
        BeanIntrospectionsProvider previous = provider;
        provider = beanIntrospectionsProvider;
        return previous;
    }

    /**
     * Restore the default {@link BeanIntrospectionsProvider}.
     */
    public static void reset() {
        provider = DEFAULT_PROVIDER;
    }
}
