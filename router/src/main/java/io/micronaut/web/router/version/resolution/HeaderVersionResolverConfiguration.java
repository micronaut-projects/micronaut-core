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
package io.micronaut.web.router.version.resolution;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.web.router.version.RoutesVersioningConfiguration;

import java.util.Collections;
import java.util.List;

import static io.micronaut.web.router.version.resolution.HeaderVersionResolverConfiguration.PREFIX;

/**
 * Configuration for version resolution via headers.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.1.0
 */
@ConfigurationProperties(PREFIX)
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
public class HeaderVersionResolverConfiguration implements Toggleable {

    public static final String PREFIX = RoutesVersioningConfiguration.PREFIX + ".header";
    public static final String DEFAULT_HEADER_NAME = "X-API-VERSION";

    private boolean enabled;
    private List<String> names = Collections.singletonList(DEFAULT_HEADER_NAME);

    /**
     * @return The header names to search for the version.
     */
    public List<String> getNames() {
        return names;
    }

    /**
     * Sets which headers should be searched for a version.
     *
     * @param names The header names
     */
    public void setNames(List<String> names) {
        this.names = names;
    }

    /**
     * @return {@code true} If headers should be searched.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether headers should be searched for a version.
     *
     * @param enabled True if headers should be searched.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
