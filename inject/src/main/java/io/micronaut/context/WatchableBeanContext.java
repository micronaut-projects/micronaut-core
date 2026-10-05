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

import io.micronaut.context.watch.BeanDefinitionWatcher;
import io.micronaut.context.watch.BeanWatch;
import io.micronaut.context.watch.BeanWatcher;
import io.micronaut.context.watch.ConfigurationWatcher;
import io.micronaut.context.watch.ExecutableMethodWatcher;
import io.micronaut.context.watch.ResourceSelector;
import io.micronaut.context.watch.ResourceWatcher;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;

/**
 * A bean context whose definitions, beans, methods, configuration and resources can be watched: a
 * watcher registers for the specific things it derives state from and receives one batched change
 * whenever they change, the startup state being the first batch.
 *
 * <p>Implemented by the default context, which is injectable as this type. A watch registered while a
 * bean is being created belongs to that bean and is closed when the bean is destroyed.</p>
 *
 * <p>This is public API for modules, and experimental: a module that derives state from the context,
 * such as a registry built from beans or a cache of what configuration says, watches it here rather
 * than listening for events, so that it follows a development reload, a definition registered at
 * runtime and a configuration refresh alike. Being experimental, it may still change in a minor
 * release. The context is the only implementation; a module tests for it with {@code instanceof}
 * and watches nothing when the context it is given is another one.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public sealed interface WatchableBeanContext extends BeanContext permits DefaultBeanContext {

    /**
     * Watches the bean definitions of a type. The watcher receives the definitions present as its first
     * batch, at startup or at once when the context is already running, then one batch per change: a
     * definition registered at runtime, or the definitions a development reload retires and adds.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier, or null for any
     * @param watcher The watcher
     * @param <T> The bean type
     * @return The watch, to close when the watcher no longer needs changes
     */
    <T> BeanWatch watchDefinitions(Argument<T> beanType, @Nullable Qualifier<T> qualifier, BeanDefinitionWatcher<T> watcher);

    /**
     * Watches the bean definitions of a type.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier, or null for any
     * @param watcher The watcher
     * @param <T> The bean type
     * @return The watch
     * @see #watchDefinitions(Argument, Qualifier, BeanDefinitionWatcher)
     */
    default <T> BeanWatch watchDefinitions(Class<T> beanType, @Nullable Qualifier<T> qualifier, BeanDefinitionWatcher<T> watcher) {
        return watchDefinitions(Argument.of(beanType), qualifier, watcher);
    }

    /**
     * Watches the beans of a type, creating them. The watcher receives the beans present as its first
     * batch, then one batch per change, with the instances that went and the ones that came. The
     * instances delivered stay the ones delivered: a prototype among the candidates is created once for
     * the watch, not again for every batch.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier, or null for any
     * @param watcher The watcher
     * @param <T> The bean type
     * @return The watch
     */
    <T> BeanWatch watchBeans(Argument<T> beanType, @Nullable Qualifier<T> qualifier, BeanWatcher<T> watcher);

    /**
     * Watches the beans of a type, creating them.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier, or null for any
     * @param watcher The watcher
     * @param <T> The bean type
     * @return The watch
     * @see #watchBeans(Argument, Qualifier, BeanWatcher)
     */
    default <T> BeanWatch watchBeans(Class<T> beanType, @Nullable Qualifier<T> qualifier, BeanWatcher<T> watcher) {
        return watchBeans(Argument.of(beanType), qualifier, watcher);
    }

    /**
     * Watches the executable methods carrying an annotation: the reload-aware form of an
     * {@link io.micronaut.context.processor.ExecutableMethodProcessor}. The watcher receives the methods
     * present as its first batch, then one batch per change.
     *
     * @param annotationType The annotation the methods carry, directly or as a stereotype
     * @param watcher The watcher
     * @param <A> The annotation type
     * @return The watch
     */
    <A extends Annotation> BeanWatch watchMethods(Class<A> annotationType, ExecutableMethodWatcher<A> watcher);

    /**
     * Watches the configuration under a prefix. The watcher is called, after the configuration beans
     * under the prefix were rebound, for every refresh that touches the prefix, and says what it did.
     * A watch registered while a bean is being created belongs to that bean, which is what lets the
     * watcher answer {@link ConfigurationWatcher.Outcome#RECREATE}.
     *
     * @param prefix The prefix, such as {@code datasources.default}
     * @param watcher The watcher
     * @return The watch
     */
    BeanWatch watchConfiguration(String prefix, ConfigurationWatcher watcher);

    /**
     * Watches the resources a selector selects: the files of a kind of resource root, by glob. The
     * watcher receives what is under the roots as its first batch, then one batch per change.
     *
     * @param selector The selector
     * @param watcher The watcher
     * @return The watch
     */
    BeanWatch watchResources(ResourceSelector selector, ResourceWatcher watcher);
}
