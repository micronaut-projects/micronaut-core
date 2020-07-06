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
package io.micronaut.discovery.config;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.naming.Described;
import org.reactivestreams.Publisher;

/**
 * A Configuration client is responsible for reading configuration for configuration servers.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ConfigurationClient extends Described {

    /**
     * The prefix used to configure the config client.
     */
    String CONFIGURATION_PREFIX = "micronaut.config-client";

    /**
     * The read timeout used when reading distributed configuration.
     */
    String ENABLED = CONFIGURATION_PREFIX + ".enabled";

    /**
     * The read timeout used when reading distributed configuration.
     */
    String READ_TIMEOUT = CONFIGURATION_PREFIX + ".read-timeout";

    /**
     * Retrieves all of the {@link PropertySource} registrations for the given environment.
     *
     * @param environment The environment
     * @return A {@link Publisher} that emits zero or many {@link PropertySource} instances discovered for the given environment
     */
    Publisher<PropertySource> getPropertySources(Environment environment);
}
