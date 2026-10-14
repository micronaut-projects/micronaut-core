/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.List;

/**
 * The default implementation of {@link BeanDefinitionsProvider}.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public final class DefaultBeanDefinitionsProvider implements BeanDefinitionsProvider {

    @Override
    public List<BeanDefinitionReference<?>> provide(ClassLoader classLoader) {
        List<BeanDefinitionReference> result = MicronautMetaServiceLoaderUtils.findMetaMicronautServiceEntries(
            classLoader,
            BeanDefinitionReference.class,
            BeanDefinitionReference::isPresent
        );
        return (List) result;
    }

    /**
     * Provides the references, lazily when asked to: a definition whose {@code META-INF/micronaut} entry carries a
     * descriptor gets a reference that answers from the descriptor and loads the definition only when it needs to.
     * The other definitions get the references {@link #provide(ClassLoader)} gives, and so do all of them in a native
     * image. Only the context's own read and the bean definition prefetch ask for lazy references, when lazy bean
     * definitions are switched on ({@code micronaut.bean-definitions.lazy=true}).
     *
     * @param classLoader The class loader to use for loading bean definitions
     * @param lazy        Whether to give lazy references
     * @return A list of bean definition references
     * @since 5.3.0
     */
    @Internal
    public List<BeanDefinitionReference<?>> provide(ClassLoader classLoader, boolean lazy) {
        if (!lazy || NativeImageUtils.inImageCode()) {
            return provide(classLoader);
        }
        return LazyBeanDefinitions.provide(classLoader);
    }

}
