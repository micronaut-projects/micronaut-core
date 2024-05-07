/*
 * Copyright 2017-2024 original authors
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

/**
 * Extends {@link BeanContext} interface and allows the bean context to be configured but not started.
 *
 * @since 4.5.0
 */
public interface ConfigurableBeanContext
    extends BeanContext {

    /**
     * Configures the bean context loading all bean definitions
     * required to perform successful startup without starting the context itself.
     *
     * <p>Once called the methods of the {@link BeanDefinitionRegistry} interface will
     * return results allowing inspection of the context without needing to run the context.</p>
     *
     * @see io.micronaut.inject.BeanDefinition
     * @see #getBeanDefinition(Class)
     * @see #start()
     */
    void configure();
}
