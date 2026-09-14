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
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * The dependent scope of the bean a resolution context resolves for, as a {@link DependentBeanContext}.
 *
 * <p>The dependents of that bean are the ones the resolution context carries: the beans created for it so far while
 * it is being created, or the beans created with it when the context was opened for its registration. Reuse and
 * creation are the {@link DefaultBeanContext}'s.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultDependentBeanContext implements DependentBeanContext {

    private final DefaultBeanContext context;
    private final BeanResolutionContext resolutionContext;

    DefaultDependentBeanContext(DefaultBeanContext context, BeanResolutionContext resolutionContext) {
        this.context = context;
        this.resolutionContext = resolutionContext;
    }

    @Override
    public <T> Collection<BeanRegistration<T>> getBeanRegistrations(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return context.getDependentBeanRegistrations(resolutionContext, beanType, qualifier);
    }

    @Override
    public <T> BeanRegistration<T> getBeanRegistration(BeanDefinition<T> definition) {
        return context.getDependentBeanRegistration(resolutionContext, definition);
    }

    @Override
    public BeanContext getContext() {
        return context;
    }
}
