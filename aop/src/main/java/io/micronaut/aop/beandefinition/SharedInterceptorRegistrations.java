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
import io.micronaut.core.annotation.Internal;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Bridges construction and lifecycle interception through the bean's creation frame. The frame starts before
 * construction, is isolated from nested bean creation, and transfers its ownership to the completed registration.
 * Older direct-instantiation paths use the resolution context's default attribute-backed implementation.
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
public final class SharedInterceptorRegistrations {

    private SharedInterceptorRegistrations() {
    }

    /**
     * Stores lifecycle candidates on the bean's creation frame.
     * @param resolutionContext The resolution context
     * @param definition The bean definition
     * @param registrations The interceptor registrations
     */
    public static void store(BeanResolutionContext resolutionContext, BeanDefinition<?> definition, @Nullable List<?> registrations) {
        if (registrations != null) {
            resolutionContext.setBeanInterceptors(definition, registrations);
        }
    }

    @SuppressWarnings("unchecked")
    static @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> peek(BeanResolutionContext resolutionContext,
                                                                         BeanDefinition<?> definition) {
        return (Collection<BeanRegistration<Interceptor<?, ?>>>) resolutionContext.getBeanInterceptors(definition);
    }
}
