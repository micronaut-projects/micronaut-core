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
package io.micronaut.context.env;

import io.micronaut.core.annotation.Experimental;

import java.util.Collection;

/**
 * An interface for beans that are capable of locating a {@link PropertySource} instance.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Experimental
public interface PropertySourcesLocator {

    /**
     * Locate a {@link PropertySource}s for the given environment.
     *
     * @param environment The environment
     * @return The located property sources
     */
    Collection<PropertySource> load(Environment environment);
}
