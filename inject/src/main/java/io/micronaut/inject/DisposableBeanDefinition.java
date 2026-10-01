/*
 * Copyright 2017-2020 original authors
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

import java.util.List;

/**
 * A bean definition that provides disposing hooks normally in the form of {@link jakarta.annotation.PreDestroy}
 * annotated methods.
 *
 * @param <T> The bean definition type
 * @author Graeme Rocher
 * @see jakarta.annotation.PreDestroy
 * @since 1.0
 */
@Internal
public interface DisposableBeanDefinition<T> extends BeanDefinition<T> {

    /**
     * Disposes of the bean definition by executing all {@link jakarta.annotation.PreDestroy} hooks.
     *
     * @param context The bean context
     * @param bean    The bean
     * @return The bean instance
     */
    default T dispose(BeanContext context, T bean) {
        try (DefaultBeanResolutionContext rc = new DefaultBeanResolutionContext(context, this)) {
            return dispose(rc, context, bean);
        }
    }

    /**
     * Disposes of the bean definition by executing all {@link jakarta.annotation.PreDestroy} hooks.
     *
     * @param resolutionContext The bean resolution context
     * @param context           The bean context
     * @param bean              The bean
     * @return The bean instance
     */
    T dispose(BeanResolutionContext resolutionContext, BeanContext context, T bean);

    /**
     * The {@link jakarta.annotation.PreDestroy} callbacks of the bean as reflection-free executable methods, in
     * the order {@link #dispose(BeanResolutionContext, BeanContext, Object)} invokes them.
     *
     * <p>Only available for a bean that intercepts its pre-destroy phase, that is one bound with
     * {@code @InterceptorBinding(kind = PRE_DESTROY)}; every other definition, including one compiled by an
     * earlier version, returns an empty list. The callbacks are not part of {@link #getExecutableMethods()}.</p>
     *
     * @return The pre-destroy callbacks, or an empty list
     * @since 5.2.0
     */
    default List<ExecutableMethod<T, ?>> getPreDestroyExecutableMethods() {
        return List.of();
    }
}
