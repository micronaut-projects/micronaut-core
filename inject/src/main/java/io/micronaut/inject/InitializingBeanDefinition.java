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

import java.util.List;

/**
 * A bean definition that is provides initialization hooks normally in the form of methods annotated with
 * {@link jakarta.annotation.PostConstruct}.
 *
 * @param <T> The bean definition type
 * @author Graeme Rocher
 * @see jakarta.annotation.PostConstruct
 * @since 1.0
 */
public interface InitializingBeanDefinition<T> extends BeanDefinition<T> {

    /**
     * Initializes the bean invoking all {@link jakarta.annotation.PostConstruct} hooks.
     *
     * @param context The bean context
     * @param bean    The bean
     * @return The bean instance
     */
    default T initialize(BeanContext context, T bean) {
        return initialize(new DefaultBeanResolutionContext(context, this), context, bean);
    }

    /**
     * Initializes the bean invoking all {@link jakarta.annotation.PostConstruct} hooks.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param bean              The bean
     * @return The bean instance
     */
    T initialize(BeanResolutionContext resolutionContext, BeanContext context, T bean);

    /**
     * The {@link jakarta.annotation.PostConstruct} callbacks of the bean as reflection-free executable methods, in
     * the order {@link #initialize(BeanResolutionContext, BeanContext, Object)} invokes them.
     *
     * <p>Only available for a bean that intercepts its post-construct phase, that is one bound with
     * {@code @InterceptorBinding(kind = POST_CONSTRUCT)}; every other definition, including one compiled by an
     * earlier version, returns an empty list. The callbacks are not part of {@link #getExecutableMethods()}.</p>
     *
     * @return The post-construct callbacks, or an empty list
     * @since 5.2.0
     */
    default List<ExecutableMethod<T, ?>> getPostConstructExecutableMethods() {
        return List.of();
    }
}
