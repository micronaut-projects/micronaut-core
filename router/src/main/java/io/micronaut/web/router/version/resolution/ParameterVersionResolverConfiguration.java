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
package io.micronaut.web.router.version.resolution;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.web.router.version.RoutesVersioningConfiguration;

import java.util.Collections;
import java.util.List;

import static io.micronaut.web.router.version.resolution.ParameterVersionResolverConfiguration.PREFIX;

/**
 * Configuration for version resolution via request parameters.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.1.0
 */
@ConfigurationProperties(PREFIX)
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
public class ParameterVersionResolverConfiguration implements Toggleable {

    public static final String PREFIX = RoutesVersioningConfiguration.PREFIX + ".parameter";
    public static final String DEFAULT_PARAMETER_NAME = "api-version";

    private boolean enabled;
    private List<String> names = Collections.singletonList(DEFAULT_PARAMETER_NAME);

    /**
     * @return The parameter names to search for the version.
     */
    public List<String> getNames() {
        return names;
    }

    /**
     * Sets which parameter should be searched for a version.
     *
     * @param names The parameter names
     */
    public void setNames(List<String> names) {
        this.names = names;
    }

    /**
     * @return {@code true} If parameter should be searched.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether parameter should be searched for a version.
     *
     * @param enabled True if parameter should be searched.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
