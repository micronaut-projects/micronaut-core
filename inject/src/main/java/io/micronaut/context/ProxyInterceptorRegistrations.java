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

import java.util.List;

/**
 * The interceptor registrations a proxy with {@code proxyTarget = true} hands to its target, the value of
 * {@link BeanResolutionContext#PROXY_INTERCEPTOR_REGISTRATIONS}.
 *
 * <p>While the proxy resolves its target the registrations are the ones the proxy was constructed with. While the
 * context creates that target they are narrowed to the non-singleton registrations the target adopted.</p>
 *
 * @param target        The definition of the proxy target
 * @param registrations The interceptor registrations
 * @author Denis Stepanov
 * @since 5.2.1
 */
@Internal
public record ProxyInterceptorRegistrations(BeanDefinition<?> target,
                                            List<? extends BeanRegistration<?>> registrations) {

    /**
     * Whether the registrations are handed to the given definition.
     *
     * @param definition The definition being created
     * @return {@code true} if the definition is the proxy target the registrations are handed to
     */
    public boolean isFor(BeanDefinition<?> definition) {
        return target == definition || target.equals(definition);
    }
}
