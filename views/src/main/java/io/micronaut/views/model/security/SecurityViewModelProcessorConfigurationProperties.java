/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.views.model.security;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.config.SecurityConfigurationProperties;

import javax.annotation.Nonnull;

/**
 * {@link ConfigurationProperties} implementation of {@link SecurityViewModelProcessorConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Requires(classes = Authentication.class)
@ConfigurationProperties(SecurityViewModelProcessorConfigurationProperties.PREFIX)
public class SecurityViewModelProcessorConfigurationProperties implements SecurityViewModelProcessorConfiguration {

    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".views-model-decorator";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default securityKey value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_SECURITYKEY = "security";

    /**
     * The default principalNameKey value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_PRINCIPALNAME = "name";

    /**
     * The default attributesKey value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_ATTRIBUTESKEY = "attributes";

    private boolean enabled = DEFAULT_ENABLED;

    @Nonnull
    private String securityKey = DEFAULT_SECURITYKEY;

    @Nonnull
    private String principalNameKey = DEFAULT_PRINCIPALNAME;

    @Nonnull
    private String attributesKey = DEFAULT_ATTRIBUTESKEY;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable {@link SecurityViewModelProcessor}. Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled enabled flag
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Model key name. Default value ({@value #DEFAULT_SECURITYKEY}).
     * @param securityKey the key name which will be used in the model map.
     */
    public void setSecurityKey(String securityKey) {
        this.securityKey = securityKey;
    }

    /**
     *
     * @return the key name which will be used in the model map.
     */
    @Override
    public String getSecurityKey() {
        return this.securityKey;
    }

    /**
     *
     * @return the key for the principal name property which is used in the nested security map.
     */
    @Nonnull
    @Override
    public String getPrincipalNameKey() {
        return this.principalNameKey;
    }

    /**
     *
     * @return the key for the attributes property which is used in the nested security map.
     */
    @Nonnull
    @Override
    public String getAttributesKey() {
        return this.attributesKey;
    }

    /**
     * Nested security map key for the user's name property. Default value ({@value #DEFAULT_PRINCIPALNAME}).
     * @param principalNameKey the key which will be used in the nested security map.
     */
    public void setPrincipalNameKey(@Nonnull String principalNameKey) {
        this.principalNameKey = principalNameKey;
    }

    /**
     * Nested security map key for the user's attributes property. Default value ({@value #DEFAULT_ATTRIBUTESKEY}).
     * @param attributesKey the key which will be used in the nested security map.
     */
    public void setAttributesKey(@Nonnull String attributesKey) {
        this.attributesKey = attributesKey;
    }
}
