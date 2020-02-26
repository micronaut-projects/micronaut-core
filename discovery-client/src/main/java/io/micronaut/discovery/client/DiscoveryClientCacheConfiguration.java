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
package io.micronaut.discovery.client;

import static io.micronaut.discovery.client.DiscoveryClientCacheConfiguration.CACHE_NAME;

import io.micronaut.cache.CacheConfiguration;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.inject.Named;
import java.time.Duration;

/**
 * A cache configuration for the Discovery client cache.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Named(CACHE_NAME)
@ConfigurationProperties(CacheConfiguration.PREFIX + "." + CACHE_NAME)
@Requires(property = DiscoveryClientCacheConfiguration.SETTING_ENABLED, notEquals = StringUtils.FALSE)
public class DiscoveryClientCacheConfiguration extends CacheConfiguration implements Toggleable {

    /**
     * The prefix to use for all discovery client settings.
     */
    public static final String CACHE_NAME = "discovery-client";

    /**
     * Configuration property name for enabled discovery cache client.
     */
    public static final String SETTING_ENABLED = CacheConfiguration.PREFIX + ".discovery-client.enabled";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;

    /**
     * @param applicationConfiguration The application configuration
     */
    @SuppressWarnings("MagicNumber")
    public DiscoveryClientCacheConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(CACHE_NAME, applicationConfiguration);
        setExpireAfterAccess(Duration.ofSeconds(30));
        setExpireAfterWrite(Duration.ofSeconds(30));
        setInitialCapacity(5);
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @return Whether the discovery client is enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled Enable or disable the discovery client
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
