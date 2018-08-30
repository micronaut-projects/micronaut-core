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
 * JWT propagation Configuration Properties.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(TokenPropagationConfigurationProperties.PREFIX)
public class TokenPropagationConfigurationProperties implements TokenPropagationConfiguration {

    public static final String PREFIX = TokenConfigurationProperties.PREFIX + ".propagation";

    private boolean enabled = false;

    private String serviceIdRegex;

    private String uriRegex;


    /**
     * @return a regular expresion to validate the service id against e.g. http://(guides|docs)\.micronaut\.io
     */
    @Override
    public String getServiceIdRegex() {
        return this.serviceIdRegex;
    }

    /**
     * setter.
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
     * setter.
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
     * setter.
     * @param enabled enabled flag
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
