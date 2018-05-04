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
import io.micronaut.security.config.SecurityConfigurationProperties;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(LogoutControllerConfigurationProperties.PREFIX)
public class LogoutControllerConfigurationProperties implements LogoutControllerConfiguration {
    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".endpoints.logout";

    private boolean enabled = false;
    private String path = "/logout";

    /**
     * @return true if you want to enable the {@link LogoutController}
     */
    @Override
    public boolean isEnabled() {
         return this.enabled;
    }

    @Override
    public String getPath() {
        return this.path;
    }
}
