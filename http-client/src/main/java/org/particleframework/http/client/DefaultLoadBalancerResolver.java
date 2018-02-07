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

import org.particleframework.context.BeanContext;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.discovery.DiscoveryClient;
import org.particleframework.discovery.ServiceInstanceList;
import org.particleframework.http.client.loadbalance.DiscoveryClientLoadBalancerFactory;
import org.particleframework.http.client.loadbalance.ServiceInstanceListLoadBalancerFactory;
import org.particleframework.runtime.server.EmbeddedServer;

import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
public class DefaultLoadBalancerResolver implements LoadBalancerResolver {
    private final Optional<EmbeddedServer> embeddedServer;
    private final Map<String, ServiceInstanceList> serviceInstanceLists;
    private final BeanContext beanContext;

    /**
     * The default server loadbalance resolver
     *
     * @param embeddedServer An optional reference to the {@link EmbeddedServer}
     * @param beanContext The bean context
     * @param serviceInstanceLists Any other providers
     */
    public DefaultLoadBalancerResolver(
            Optional<EmbeddedServer> embeddedServer,
            BeanContext beanContext,
            ServiceInstanceList...serviceInstanceLists) {
        this.embeddedServer = embeddedServer;
        this.beanContext = beanContext;
        if(ArrayUtils.isNotEmpty(serviceInstanceLists)) {
            this.serviceInstanceLists = new HashMap<>(serviceInstanceLists.length);
            for (ServiceInstanceList provider : serviceInstanceLists) {
                this.serviceInstanceLists.put(provider.getID(), provider);
            }
        }
        else {
            this.serviceInstanceLists = Collections.emptyMap();
        }
    }

    @Override
    public Optional<? extends LoadBalancer> resolve(String... serviceReferences) {
        if(ArrayUtils.isEmpty(serviceReferences) || StringUtils.isEmpty(serviceReferences[0])) {
            return Optional.empty();
        }
        String reference = serviceReferences[0];

        if(serviceInstanceLists.containsKey(reference)) {
            ServiceInstanceList serviceInstanceList = serviceInstanceLists.get(reference);
            LoadBalancer loadBalancer = beanContext.getBean(ServiceInstanceListLoadBalancerFactory.class).create(serviceInstanceList);
            return Optional.ofNullable(loadBalancer);
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
            LoadBalancer loadBalancer = beanContext.getBean(DiscoveryClientLoadBalancerFactory.class).create(serviceReferences[0]);
            return Optional.of(loadBalancer);
        }
        return Optional.empty();
    }


    /**
     * Creates the default {@link LoadBalancer} implementation. Subclasses can override to provide custom load balancing strategies
     *
     *
     * @param serviceId The service ID
     * @return The {@link LoadBalancer}
     */
    protected LoadBalancer createServiceInstanceLoadBalancer(String serviceId) {
        return beanContext.createBean(LoadBalancer.class, serviceId);
    }

}
