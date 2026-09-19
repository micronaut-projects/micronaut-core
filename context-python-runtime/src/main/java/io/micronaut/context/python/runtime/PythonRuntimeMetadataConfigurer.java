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
package io.micronaut.context.python.runtime;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.BeanContextConfiguration;
import io.micronaut.context.BeanDefinitionsProvider;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.core.annotation.Internal;

/**
 * Composes the runtime references into whatever bean definitions provider the application configured, and installs
 * the introspection provider, when a context is built. Runs last so that no other configurer's provider is lost.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonRuntimeMetadataConfigurer implements ApplicationContextConfigurer {

    /**
     * Creates the configurer.
     */
    public PythonRuntimeMetadataConfigurer() {
    }

    @Override
    public void configure(ApplicationContextBuilder builder) {
        BeanDefinitionsProvider configured = builder instanceof BeanContextConfiguration configuration
            ? configuration.getBeanDefinitionsProvider() : new DefaultBeanDefinitionsProvider();
        if (!(configured instanceof PythonRuntimeBeanDefinitionsProvider)) {
            builder.beanDefinitionsProvider(new PythonRuntimeBeanDefinitionsProvider(configured));
        }
        PythonRuntimeIntrospectionsProvider.install();
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
