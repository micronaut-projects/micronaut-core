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

package io.micronaut.security.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stores configuration for JWT.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(SecurityConfiguration.PREFIX)
public class SecurityConfiguration implements Toggleable {

    public static final String PREFIX = "micronaut.security";
    public static final String ANYWHERE = "0.0.0.0";

    protected boolean enabled = false;
    protected SecurityConfigType securityConfigType = SecurityConfigType.INTERCEPT_URL_MAP;
    protected List<InterceptUrlMapPattern> interceptUrlMap = new ArrayList<>();
    protected List<String> ipPatterns = Arrays.asList(ANYWHERE);

    /**
     * enabled getter.
     * @return boolean flag indicating whether the security features are enabled.
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * securityConfigType getter.
     * @return an enum containing the type of security configuration
     */
    public SecurityConfigType getSecurityConfigType() {
        return this.securityConfigType;
    }

    /**
     * interceptUrlMap getter.
     * @return a list of {@link InterceptUrlMapPattern}
     */
    public List<InterceptUrlMapPattern> getInterceptUrlMap() {
        return interceptUrlMap;
    }

    /**
     * ipPatterns getter.
     * @return a list of IP Regex patterns. e.g. [192.168.1.*]
     */
    public List<String> getIpPatterns() {
        return ipPatterns;
    }
}
