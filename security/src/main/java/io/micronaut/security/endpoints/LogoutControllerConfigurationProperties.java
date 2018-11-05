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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.security.config.SecurityConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of {@link LogoutControllerConfiguration} used to configure the {@link LogoutController}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = LogoutControllerConfigurationProperties.PREFIX + ".enabled", value = StringUtils.TRUE)
@ConfigurationProperties(LogoutControllerConfigurationProperties.PREFIX)
public class LogoutControllerConfigurationProperties implements LogoutControllerConfiguration {
    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".endpoints.logout";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    /**
     * The default path.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_PATH = "/logout";

    /**
     * Default Allowed Http Method.
     */
    @SuppressWarnings("WeakerAccess")
    public static final HttpMethod DEFAULT_HTTPMETHOD = HttpMethod.POST;

    private static final List<HttpMethod> SUPPORTED_ALLOWEDMETHODS = new ArrayList<HttpMethod>() {{
        add(HttpMethod.GET);
        add(HttpMethod.POST);
    }};

    private boolean enabled = DEFAULT_ENABLED;
    private String path = DEFAULT_PATH;
    private List<HttpMethod> allowedMethods = Collections.singletonList(DEFAULT_HTTPMETHOD);

    @Override
    public List<HttpMethod> getAllowedMethods() {
        return allowedMethods;
    }

    /**
     * Allowed HTTP Methods for {@link io.micronaut.security.endpoints.LogoutController}. Default value [POST]. Only GET or POST are valid values.
     * @param allowedMethods a List of Allowed methods. Only GET or POST are valid values.
     */
    public void setAllowedMethods(List<HttpMethod> allowedMethods) {
        if (allowedMethods.stream().anyMatch(method -> !SUPPORTED_ALLOWEDMETHODS.contains(method))) {
            String supportedMethodsCsv = SUPPORTED_ALLOWEDMETHODS.stream().map(HttpMethod::toString).reduce((a, b) -> a + ", " + b).get();
            String allowedMethodsCsv = allowedMethods.stream().map(HttpMethod::toString).reduce((a, b) -> a + ", " + b).get();
            throw new IllegalArgumentException(supportedMethodsCsv + " are the only values supported for " + LogoutControllerConfigurationProperties.PREFIX + ".allowed-methods, you supplied: " + allowedMethodsCsv);
        }
        this.allowedMethods = allowedMethods;
    }

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

    /**
     * Enables {@link io.micronaut.security.endpoints.LogoutController}. Default value {@value #DEFAULT_ENABLED}.
     *
     * @param enabled true if it is
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Path to the {@link io.micronaut.security.endpoints.LogoutController}. Default value {@value #DEFAULT_PATH}.
     * @param path The path
     */
    public void setPath(String path) {
        this.path = path;
    }
}
