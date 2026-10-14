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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.List;

/**
 * The provider of bean definitions.
 *
 * <p>With the experimental system property {@code micronaut.bean-definitions.lazy=true}, the references a context
 * reads with its default provider may be lazy: such a reference is not a
 * {@link io.micronaut.inject.BeanDefinition} and loads its definition only when it needs to. Its
 * {@link io.micronaut.inject.BeanDefinitionReference#isEnabled(BeanContext)} checks only the conditions that can be
 * checked before the definition is loaded, the others are checked on the definition, and its
 * {@link io.micronaut.inject.BeanDefinitionReference#load(BeanContext)} returns {@code null} when the definition cannot
 * be linked. A provider of the application gets what {@link #provide(ClassLoader)} of
 * {@link DefaultBeanDefinitionsProvider} gives, which is never lazy.</p>
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Experimental
public interface BeanDefinitionsProvider {

    /**
     * Provides a list of bean definition references available from the given class loader.
     *
     * @param classLoader The class loader to use for loading bean definitions
     * @return A list of bean definition references
     */
    List<BeanDefinitionReference<?>> provide(ClassLoader classLoader);

}
