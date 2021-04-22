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
package io.micronaut.context;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.annotation.ConfigurationReader;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

/**
 * Configuration for the {@link BeanContext}.
 *
 * @author graemerocher
 * @since 1.1
 */
public interface BeanContextConfiguration {

    /**
     * The class loader to use.
     * @return The class loader.
     */
    default @NonNull ClassLoader getClassLoader() {
        return ApplicationContextConfiguration.class.getClassLoader();
    }

    /**
     * Whether eager initialization of singletons is enabled.
     * @return True if eager initialization of singletons is enabled
     * @since 2.0
     */
    default boolean isEagerInitSingletons() {
        return getEagerInitAnnotated().contains(Singleton.class);
    }

    /**
     * Whether eager initialization of {@link io.micronaut.context.annotation.ConfigurationProperties} is enabled.
     * @return True if eager initialization of configuration is enabled
     * @since 2.0
     */
    default boolean isEagerInitConfiguration() {
        return getEagerInitAnnotated().contains(ConfigurationReader.class);
    }

    /**
     * @return A set of annotated classes that should be eagerly initialized
     */
    default Set<Class<? extends Annotation>> getEagerInitAnnotated() {
        return Collections.emptySet();
    }
}
