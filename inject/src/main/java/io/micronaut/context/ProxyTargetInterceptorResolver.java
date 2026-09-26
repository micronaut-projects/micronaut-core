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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;

/**
 * Creates the interceptors of the methods of a proxy target together with the target.
 *
 * <p>A proxy that fronts a separate target selects the non-singleton interceptors of its methods from the target's
 * registration, creating those the target has not got yet. An interceptor bound only for {@code AROUND} would then be
 * created on the first selection, after the target: after its creation event for a proxy that resolves its target as
 * it is constructed, and on the first call for a lazy one. The context calls this as each proxy target is created,
 * before the {@link io.micronaut.context.event.BeanCreatedEvent} of the target, so that they are created with the
 * target as its dependents, where the selection then finds them and the creation event reports them.</p>
 *
 * <p>Implemented by the AOP module, which is what knows which interceptors a proxy binds, and found with the service
 * loader.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface ProxyTargetInterceptorResolver {

    /**
     * Creates the non-singleton interceptors bound to the methods of the given proxy target through the resolution
     * context creating it, so that they become its dependents.
     *
     * @param resolutionContext The resolution context the target is created in
     * @param target            The definition of the target
     */
    void resolveInterceptors(BeanResolutionContext resolutionContext, BeanDefinition<?> target);
}
