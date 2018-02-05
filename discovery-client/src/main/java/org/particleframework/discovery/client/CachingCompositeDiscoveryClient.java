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

import io.reactivex.Flowable;
import org.particleframework.cache.annotation.Cacheable;
import org.particleframework.context.annotation.Primary;
import org.particleframework.context.annotation.Replaces;
import org.particleframework.context.annotation.Requires;
import org.particleframework.discovery.CompositeDiscoveryClient;
import org.particleframework.discovery.DiscoveryClient;
import org.particleframework.discovery.ServiceInstance;

import java.util.List;

/**
 * Replaces the default {@link CompositeDiscoveryClient} with one that caches the return values
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Replaces(CompositeDiscoveryClient.class)
@Primary
@Requires(property = DiscoveryClientCacheConfiguration.SETTING_ENABLED, notEquals = "false")
public class CachingCompositeDiscoveryClient extends CompositeDiscoveryClient {
    public CachingCompositeDiscoveryClient(DiscoveryClient[] discoveryClients) {
        super(discoveryClients);
    }

    @Override
    @Cacheable(DiscoveryClientCacheConfiguration.CACHE_NAME)
    public Flowable<List<ServiceInstance>> getInstances(String serviceId) {
        return super.getInstances(serviceId);
    }

    @Override
    @Cacheable(DiscoveryClientCacheConfiguration.CACHE_NAME)
    public Flowable<List<String>> getServiceIds() {
        return super.getServiceIds();
    }
}
