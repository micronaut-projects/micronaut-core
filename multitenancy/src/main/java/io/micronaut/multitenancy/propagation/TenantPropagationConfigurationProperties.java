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

package io.micronaut.multitenancy.propagation;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.multitenancy.MultitenancyConfiguration;

/**
 * Tenant propagation Configuration Properties.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(TenantPropagationConfigurationProperties.PREFIX)
public class TenantPropagationConfigurationProperties implements TenantPropagationConfiguration {

    public static final String PREFIX = MultitenancyConfiguration.PREFIX + ".propagation";

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

    private String serviceIdRegex;

    private String uriRegex;

    private String path = DEFAULT_PATH;

    /**
     * @return a regular expresion to validate the service id against.
     */
    @Override
    public String getServiceIdRegex() {
        return this.serviceIdRegex;
    }

    /**
     * Regular expression to match service ID.
     * @param serviceIdRegex serviceId regular expression
     */
    public void setServiceIdRegex(String serviceIdRegex) {
        this.serviceIdRegex = serviceIdRegex;
    }


    /**
     *
     * @return a regular expression to validate the target request uri against.
     */
    @Override
    public String getUriRegex() {
        return this.uriRegex;
    }

    /**
     * Regular expression to match URI.
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
     * Whether {@link io.micronaut.multitenancy.propagation.TenantPropagationHttpClientFilter} should be enabled. Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled enabled flag
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    /**
     * Configures {@link io.micronaut.multitenancy.propagation.TenantPropagationHttpClientFilter} path. Default value {@value #DEFAULT_PATH}
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
