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
package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Blocking;

import java.util.Collections;

/**
 * Allows blocking resolving of {@link PropertySource} from remote distributed configuration servers.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BootstrapPropertySourceLocator {

    /**
     * An empty version that does nothing.
     */
    BootstrapPropertySourceLocator EMPTY_LOCATOR = environment -> Collections.emptySet();

    /**
     * A blocking interface that will attempt to resolve either remote or local {@link PropertySource} instances
     * for the current Environment.
     *
     * @param environment The environment
     * @return An iterable of {@link PropertySource}
     * @throws ConfigurationException If the resolve fails and fail fast is set to true
     */
    @Blocking
    Iterable<PropertySource> findPropertySources(Environment environment) throws ConfigurationException;
}
