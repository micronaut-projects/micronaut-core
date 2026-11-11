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
import java.util.List;

/**
 * The candidates the dependency owner of a bean retained while the bean was created. A retained set is
 * authoritative, an empty one included, so no later source is asked once it exists.
 *
 * @since 5.3.0
 */
@Internal
final class RetainedLifecycleCandidates implements LifecycleCandidateSource {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"}) // The resolution context keeps the wildcard-list boundary.
    public @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> findLifecycleCandidates(
        BeanResolutionContext resolutionContext,
        BeanDefinition<?> definition,
        ExecutableMethod<?, ?> method,
        Object bean,
        InterceptorKind kind) {
        return (List) resolutionContext.getBeanInterceptors(definition);
    }
}
