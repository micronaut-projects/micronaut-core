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

package io.micronaut.security.token.propagation;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.security.token.config.TokenConfigurationProperties;

/**
 * Token Propagation Configuration Properties.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(TokenPropagationConfigurationProperties.PREFIX)
public class TokenPropagationConfigurationProperties implements TokenPropagationConfiguration {

    public static final String PREFIX = TokenConfigurationProperties.PREFIX + ".propagation";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    /**
     * The default path.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_PATH = "/**";

    private boolean enabled = DEFAULT_ENABLED;

    private String servicesRegex;

    private String uriRegex;

    private String path = DEFAULT_PATH;

    /**
     * @return a regular expression to match the service.
     */
    @Override
    public String getServicesRegex() {
        return this.servicesRegex;
    }

    /**
     * a regular expression to match the service id.
     * @param servicesRegex serviceId regular expression
     */
    public void setServicesRegex(String servicesRegex) {
        this.servicesRegex = servicesRegex;
    }

    /**
     *
     * @return a regular expression to match the uri.
     */
    @Override
    public String getUriRegex() {
        return this.uriRegex;
    }

    /**
     * a regular expression to match the uri.
     * @param uriRegex uri regular expression
     */
    public void setUriRegex(String uriRegex) {
        this.uriRegex = uriRegex;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables {@link io.micronaut.security.token.propagation.TokenPropagationHttpClientFilter}. Default value {@value #DEFAULT_ENABLED}
     * @param enabled enabled flag
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    /**
     * Configures {@link io.micronaut.security.token.propagation.TokenPropagationHttpClientFilter} path. Default value {@value #DEFAULT_PATH}
     * @param path Path to be matched by Token Propagation Filter.
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     *
     * @return Path to be matched by Token Propagation Filter.
     */
    @Override
    public String getPath() {
        return this.path;
    }
}
