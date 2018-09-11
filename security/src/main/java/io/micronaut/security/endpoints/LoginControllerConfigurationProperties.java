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

package io.micronaut.security.endpoints;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.StringUtils;
import io.micronaut.security.config.SecurityConfigurationProperties;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(LoginControllerConfigurationProperties.PREFIX)
public class LoginControllerConfigurationProperties implements LoginControllerConfiguration {
    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".endpoints.login";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    /**
     * The default path.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_PATH = "/login";

    private boolean enabled = DEFAULT_ENABLED;
    private String path = DEFAULT_PATH;

    /**
     * @return true if you want to enable the {@link LoginController}
     */
    @Override
    public boolean isEnabled() {
         return this.enabled;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    /**
     * Enables {@link io.micronaut.security.endpoints.LoginController}. Default value {@value #DEFAULT_ENABLED}
     * @param enabled True if it is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Path to the {@link io.micronaut.security.endpoints.LoginController}. Default value {@value #DEFAULT_PATH}
     * @param path The path
     */
    public void setPath(String path) {
        if (StringUtils.isNotEmpty(path)) {
            this.path = path;
        }
    }
}
