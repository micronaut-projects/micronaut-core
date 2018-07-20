/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.management.endpoint;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.Optional;

/**
 * The default {@link Endpoint} configuration.
 *
 * @author James Kleeh
 * @since 1.0
 */
@ConfigurationProperties(EndpointDefaultConfiguration.PREFIX)
public class EndpointDefaultConfiguration {

    /**
     * The prefix for endpoints settings.
     */
    public static final String PREFIX = "endpoints.all";

    /**
     * The default base path
     */
    public static final String DEFAULT_ENDPOINT_BASE_PATH = "/";

    protected Optional<Boolean> enabled = Optional.empty();
    protected Optional<Boolean> sensitive = Optional.empty();
    protected String path = DEFAULT_ENDPOINT_BASE_PATH;

    /**
     *
     * @return endpoints Base Path (defaults to: {@value #DEFAULT_ENDPOINT_BASE_PATH})
     */
    public String getPath() {
        return path;
    }

    /**
     * @return Whether the endpoint is enabled
     */
    public Optional<Boolean> isEnabled() {
        return enabled;
    }

    /**
     * @return Does the endpoint expose sensitive information
     */
    public Optional<Boolean> isSensitive() {
        return sensitive;
    }

}
