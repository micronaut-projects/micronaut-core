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
package org.particleframework.http.client;

import io.reactivex.Flowable;
import org.particleframework.context.event.ApplicationEventListener;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.discovery.DiscoveryClient;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.http.client.loadbalance.SimpleRoundRobinLoadBalancer;
import org.particleframework.runtime.context.scope.refresh.RefreshEvent;
import org.particleframework.runtime.server.EmbeddedServer;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Abstraction over {@link LoadBalancer} lookup. The strategy is as follows:</p>
 *
 * <ul>
 *     <li>If a reference starts with '/' then we attempt to look up the {@link EmbeddedServer}</li>
 *     <li>If the reference contains a '/' assume it is a URL and try to create a URL reference to it</li>
 *     <li>Otherwise delegate to the {@link DiscoveryClient} to attempt to resolve the URIs</li>
 * </ul>
 *
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultLoadBalancerResolver implements LoadBalancerResolver, ApplicationEventListener<RefreshEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultLoadBalancerResolver.class);
    private final Optional<EmbeddedServer> embeddedServer;
    private final Map<String, LoadBalancerProvider> loadBalancerProviderMap;
    private final Provider<DiscoveryClient> discoveryClient;
    private final Map<String, Optional<ServiceInstanceLoadBalancer>> instanceSelectorMap = new ConcurrentHashMap<>();

    /**
     * The default server loadbalance resolver
     *
     * @param embeddedServer An optional reference to the {@link EmbeddedServer}
     * @param discoveryClient The discovery client
     * @param providers Any other providers
     */
    public DefaultLoadBalancerResolver(
            Optional<EmbeddedServer> embeddedServer,
            Provider<DiscoveryClient> discoveryClient,
            LoadBalancerProvider...providers) {
        this.embeddedServer = embeddedServer;
        this.discoveryClient = discoveryClient;
        if(ArrayUtils.isNotEmpty(providers)) {
            this.loadBalancerProviderMap = new HashMap<>(providers.length);
            for (LoadBalancerProvider provider : providers) {
                loadBalancerProviderMap.put(provider.getId(), provider);
            }
        }
        else {
            this.loadBalancerProviderMap = Collections.emptyMap();
        }
    }

    @Override
    public Optional<? extends LoadBalancer> resolve(String... serviceReferences) {
        if(ArrayUtils.isEmpty(serviceReferences) || StringUtils.isEmpty(serviceReferences[0])) {
            return Optional.empty();
        }
        String reference = serviceReferences[0];

        if(loadBalancerProviderMap.containsKey(reference)) {
            return Optional.ofNullable(loadBalancerProviderMap.get(reference).getLoadBalancer());
        }
        else if(reference.startsWith("/")) {
            // current server reference
            if(embeddedServer.isPresent()) {
                URL url = embeddedServer.get().getURL();
                return Optional.of(LoadBalancer.fixed(url));
            }
            else {
                return Optional.empty();
            }
        }
        else if(reference.indexOf('/') > -1) {
            try {
                URL url = new URL(reference);
                return Optional.of(LoadBalancer.fixed(url));
            } catch (MalformedURLException e) {
                return Optional.empty();
            }
        }
        else if(serviceReferences.length == 1){
            // if we've arrived here we have a reference to a service that requires service discovery
            // since this is only done at startup it is ok to block
            return instanceSelectorMap.computeIfAbsent(reference, serviceId -> {
                DiscoveryClient client = this.discoveryClient.get();
                try {
                    List<ServiceInstance> serviceInstances = Flowable.fromPublisher(client.getInstances(serviceId)).blockingFirst();

                    if(!serviceInstances.isEmpty()) {
                        ServiceInstanceLoadBalancer roundRobinServerSelector = createServerSelector(serviceId, serviceInstances);
                        return Optional.of(roundRobinServerSelector);
                    }
                    return Optional.empty();
                } catch (Exception e) {
                    if(LOG.isErrorEnabled()) {
                        LOG.error("Error resolving server list from discovery client: " + e.getMessage(), e);
                    }
                    return Optional.empty();
                }
            });

        }
        return Optional.empty();
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        Set<Map.Entry<String, Optional<ServiceInstanceLoadBalancer>>> entries = instanceSelectorMap.entrySet();
        for (Map.Entry<String, Optional<ServiceInstanceLoadBalancer>> entry : entries) {
                Optional<ServiceInstanceLoadBalancer> selector = entry.getValue();
                String serviceId = entry.getKey();
                if(selector.isPresent()) {

                    DiscoveryClient client = this.discoveryClient.get();
                    client.getInstances(serviceId).subscribe(new Subscriber<List<ServiceInstance>>() {
                        @Override
                        public void onSubscribe(Subscription subscription) {
                            subscription.request(1);
                        }

                        @Override
                        public void onNext(List<ServiceInstance> serviceInstances) {
                            selector.get().update(serviceInstances);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            if(LOG.isErrorEnabled()) {
                                LOG.error("Error resolving updated server list from discovery client: " + throwable.getMessage(), throwable);
                            }
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
                }
        }
    }

    /**
     * Creates the default {@link ServiceInstanceLoadBalancer} implementation. Subclasses can override to provide custom load balancing strategies
     *
     *
     * @param serviceId The service ID
     * @param serviceInstances A list of {@link ServiceInstance} associated with the ID
     * @return The {@link ServiceInstanceLoadBalancer}
     */
    protected ServiceInstanceLoadBalancer createServerSelector(String serviceId, List<ServiceInstance> serviceInstances) {
        return new SimpleRoundRobinLoadBalancer(serviceId, serviceInstances);
    }
}
