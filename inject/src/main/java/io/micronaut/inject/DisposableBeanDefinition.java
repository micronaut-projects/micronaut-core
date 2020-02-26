/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

/**
 * A bean definition that provides disposing hooks normally in the form of {@link javax.annotation.PreDestroy}
 * annotated methods.
 *
 * @param <T> The bean definition type
 * @author Graeme Rocher
 * @see javax.annotation.PreDestroy
 * @since 1.0
 */
public interface DisposableBeanDefinition<T> extends BeanDefinition<T> {

    /**
     * Disposes of the bean definition by executing all {@link javax.annotation.PreDestroy} hooks.
     *
     * @param context The bean context
     * @param bean    The bean
     * @return The bean instance
     */
    default T dispose(BeanContext context, T bean) {
        return dispose(new DefaultBeanResolutionContext(context, this), context, bean);
    }

    /**
     * Disposes of the bean definition by executing all {@link javax.annotation.PreDestroy} hooks.
     *
     * @param resolutionContext The bean resolution context
     * @param context           The bean context
     * @param bean              The bean
     * @return The bean instance
     */
    T dispose(BeanResolutionContext resolutionContext, BeanContext context, T bean);
}
