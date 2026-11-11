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
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * One place the interceptor candidates of a lifecycle callback can come from. The candidate resolver asks its
 * sources in order and uses the first answer.
 *
 * @since 5.3.0
 */
@Internal
sealed interface LifecycleCandidateSource permits RetainedLifecycleCandidates, LegacyLifecycleCandidates {

    /**
     * Finds the candidates for a lifecycle callback of a bean.
     *
     * @param resolutionContext The resolution context
     * @param definition The lifecycle owner
     * @param method The lifecycle method
     * @param bean The bean instance
     * @param kind The lifecycle kind
     * @return The candidates, an empty collection included, or null when this source has none to offer
     */
    @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> findLifecycleCandidates(
        BeanResolutionContext resolutionContext,
        BeanDefinition<?> definition,
        ExecutableMethod<?, ?> method,
        Object bean,
        InterceptorKind kind);
}
