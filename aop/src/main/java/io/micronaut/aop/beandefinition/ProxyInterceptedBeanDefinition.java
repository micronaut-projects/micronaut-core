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

import io.micronaut.aop.chain.LifecycleInterception;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.InstantiatableBeanDefinition;

/**
 * Intercepted {@link InstantiatableBeanDefinition} that carries constructor interceptor metadata
 * for runtime proxy beans.
 *
 * @param <T> The bean definition type
 * @author Denis Stepanov
 * @since 5.1.0
 */
@Deprecated(since = "5.3.0", forRemoval = true)
@Internal
public interface ProxyInterceptedBeanDefinition<T> extends InterceptedBeanDefinition<T> {

    /**
     * Number of internal constructor parameters appended to a generated proxy's constructor: the resolution context,
     * the bean context and the qualifier.
     *
     * @deprecated Since 5.3.0 the count is {@link LifecycleInterception#PROXY_CONSTRUCTOR_PARAMETERS}, the default
     * of every instantiation, and this interface adds nothing to its parent.
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    int ADDITIONAL_PROXY_CONSTRUCTOR_PARAMETERS_COUNT = LifecycleInterception.PROXY_CONSTRUCTOR_PARAMETERS;
}
