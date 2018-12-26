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

package io.micronaut.web.router.version;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;

import java.util.Collections;
import java.util.List;

import static io.micronaut.web.router.version.RoutesVersioningConfiguration.PREFIX;

/**
 * Routes versioning configuration.
 *
 * @author Bogdan Oros
 * @since 1.1.0
 */
@ConfigurationProperties(PREFIX)
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
public class RoutesVersioningConfiguration implements Toggleable {

    /**
     * The configuration property.
     */
    public static final String PREFIX = "micronaut.router.versioning";

    /**
     * The default enable value.
     */
    private static final boolean DEFAULT_ENABLED = false;

    /**
     * The enable value.
     */
    private boolean enabled = DEFAULT_ENABLED;

    /**
     * Request header based versioning configuration.
     */
    private HeaderBasedVersioningConfiguration header = new HeaderBasedVersioningConfiguration();

    /**
     * Request parameter based versioning configuration.
     */
    private ParameterBasedVersioningConfiguration parameter = new ParameterBasedVersioningConfiguration();

    /**
     * @return The header based versioning configuration.
     */
    public HeaderBasedVersioningConfiguration getHeader() {
        return header;
    }

    /**
     * @param header The header based versioning configuration.
     */
    public void setHeader(HeaderBasedVersioningConfiguration header) {
        this.header = header;
    }

    /**
     * @return The request parameter based versioning configuration.
     */
    public ParameterBasedVersioningConfiguration getParameter() {
        return parameter;
    }

    /**
     * @param parameter The request parameter based versioning configuration.
     */
    public void setParameter(ParameterBasedVersioningConfiguration parameter) {
        this.parameter = parameter;
    }

    /**
     * @param enabled Enables the version based route matches filtering.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return {@code true} if version based matches filtering is enabled.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Abstract specific configuration for different versioning configurations.
     */
    private abstract static class AbstractVersioningConfiguration implements Toggleable {

        private boolean enabled;
        private List<String> names;

        AbstractVersioningConfiguration(List<String> names) {
            this.names = names;
        }

        /**
         * @return property names to extract specified version from.
         */
        public List<String> getNames() {
            return names;
        }

        /**
         * @param names property names to extract specified version from.
         */
        public void setNames(List<String> names) {
            this.names = names;
        }

        /**
         * @return {@code true} if specific version based matches filtering is enabled.
         */
        @Override
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled Enables the specific version based route matches filtering.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * The request header based versioning configuration.
     */
    @ConfigurationProperties("header")
    public static class HeaderBasedVersioningConfiguration extends AbstractVersioningConfiguration {

        public static final String PREFIX = RoutesVersioningConfiguration.PREFIX + ".header";

        /**
         * Constructor for header based versioning configuration.
         * Specifies the default name of the request header.
         */
        public HeaderBasedVersioningConfiguration() {
            super(Collections.singletonList("X-API-VERSION"));
        }

    }

    /**
     * The request parameter based versioning configuration.
     */
    @ConfigurationProperties("parameter")
    public static class ParameterBasedVersioningConfiguration extends AbstractVersioningConfiguration {

        public static final String PREFIX = RoutesVersioningConfiguration.PREFIX + ".parameter";

        /**
         * Constructor for request parameter based versioning configuration.
         * Specifies the default name of the request parameter.
         */
        public ParameterBasedVersioningConfiguration() {
            super(Collections.singletonList("api-version"));
        }

    }

}
