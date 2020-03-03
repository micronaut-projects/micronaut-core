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

import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.CompositeDiscoveryClient;
import io.micronaut.discovery.DefaultCompositeDiscoveryClient;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.reactivex.Flowable;

import java.util.List;

/**
 * Replaces the default {@link io.micronaut.discovery.DefaultCompositeDiscoveryClient} with one that caches the return
 * values.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Primary
@Requires(property = DiscoveryClientCacheConfiguration.SETTING_ENABLED, notEquals = StringUtils.FALSE)
@Replaces(DefaultCompositeDiscoveryClient.class)
public class CachingCompositeDiscoveryClient extends CompositeDiscoveryClient {

    /**
     * @param discoveryClients The discovery clients
     */
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
