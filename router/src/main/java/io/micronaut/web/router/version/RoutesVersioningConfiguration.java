/*
 * Copyright 2017-2020 original authors
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

import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.Optional;

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
    private static final boolean DEFAULT_ENABLED = false;
    private boolean enabled = DEFAULT_ENABLED;
    private String defaultVersion;

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
     * @return The version to use if none can be resolved
     */
    public Optional<String> getDefaultVersion() {
        return Optional.ofNullable(defaultVersion);
    }

    /**
     * Sets the version to use if the version cannot be resolved. Default value (null).
     *
     * @param defaultVersion The default version
     */
    public void setDefaultVersion(@Nullable String defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

}
