/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.client;

import org.particleframework.cache.CacheConfiguration;
import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.context.annotation.Requires;
import org.particleframework.core.util.Toggleable;
import org.particleframework.runtime.ApplicationConfiguration;

import javax.inject.Named;

import java.time.Duration;

import static org.particleframework.discovery.client.DiscoveryClientCacheConfiguration.CACHE_NAME;

/**
 * A cache configuration for the Discovery client cache
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Named(CACHE_NAME)
@ConfigurationProperties(CACHE_NAME)
@Requires(property = DiscoveryClientCacheConfiguration.SETTING_ENABLED, notEquals = "false")
public class DiscoveryClientCacheConfiguration extends CacheConfiguration implements Toggleable {

    public static final String CACHE_NAME = "discoveryClient";
    public static final String SETTING_ENABLED =  CacheConfiguration.PREFIX + ".discoveryClient.enabled";

    private boolean enabled = true;

    public DiscoveryClientCacheConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(CACHE_NAME, applicationConfiguration);
        setExpireAfterAccess(Duration.ofSeconds(30));
        setExpireAfterWrite(Duration.ofSeconds(30));
        setInitialCapacity(5);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
