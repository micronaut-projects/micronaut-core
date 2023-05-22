/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

/**
 * <p>An type of {@link BeanDefinition} that supports post initialization bean dependencies injection.</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 4.0
 */
@Internal
public interface InjectableBeanDefinition<T> extends BeanDefinition<T> {

    /**
     * Inject the given bean with the context.
     *
     * @param context The context
     * @param bean    The bean
     * @return The injected bean
     */
    @NonNull
    default T inject(@NonNull BeanContext context, @NonNull T bean) {
        try (DefaultBeanResolutionContext resolutionContext = new DefaultBeanResolutionContext(context, this)) {
            return inject(resolutionContext, context, bean);
        }
    }

    /**
     * Inject the given bean with the context.
     *
     * @param resolutionContext the resolution context
     * @param context           The context
     * @param bean              The bean
     * @return The injected bean
     */
    @NonNull
    T inject(@NonNull BeanResolutionContext resolutionContext, @NonNull BeanContext context, @NonNull T bean);

}
