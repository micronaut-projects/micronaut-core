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
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

/**
 * An injected resolver whose dependencies are held by its own registration, which is a dependent of the consumer.
 *
 * @since 5.3.0
 */
@Internal
final class DefaultBeanDependencyResolver implements BeanDependencyResolver {
    private final DefaultBeanContext context;
    final BeanDependencies dependencies = new BeanDependencies();

    DefaultBeanDependencyResolver(DefaultBeanContext context) {
        this.context = context;
    }

    @Override
    public <T> T getBean(Argument<T> type, @Nullable Qualifier<T> qualifier) {
        return getBeanRegistration(type, qualifier).getBean();
    }

    @Override
    public <T> BeanRegistration<T> getBeanRegistration(Argument<T> type, @Nullable Qualifier<T> qualifier) {
        return dependencies.resolve(context, null, resolution -> {
            BeanRegistration<T> registration = context.getBeanRegistration(resolution, type, qualifier);
            resolution.require(registration);
            return registration;
        });
    }
}
