/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.http.client.loadbalance.DiscoveryClientLoadBalancerFactory;
import io.micronaut.http.client.loadbalance.ServiceInstanceListLoadBalancerFactory;
import io.micronaut.runtime.server.EmbeddedServer;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * <p>Abstraction over {@link LoadBalancer} lookup. The strategy is as follows:</p>
 *
 * <ul>
 * <li>If a reference starts with '/' then we attempt to look up the {@link EmbeddedServer}</li>
 * <li>If the reference contains a '/' assume it is a URL and try to create a URL reference to it</li>
 * <li>Otherwise delegate to the {@link io.micronaut.discovery.DiscoveryClient} to attempt to resolve the URIs</li>
 * </ul>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@BootstrapContextCompatible
public class DefaultLoadBalancerResolver implements LoadBalancerResolver {

    private final Map<String, ServiceInstanceList> serviceInstanceLists;
    private final BeanContext beanContext;

    /**
     * The default server loadbalance resolver.
     *
     * @param beanContext          The bean context
     * @param serviceInstanceLists Any other providers
     */
    public DefaultLoadBalancerResolver(
        BeanContext beanContext,
        ServiceInstanceList... serviceInstanceLists) {
        this(beanContext, Arrays.asList(serviceInstanceLists));
    }

    /**
     * The default server loadbalance resolver.
     *
     * @param beanContext          The bean context
     * @param serviceInstanceLists Any other providers
     */
    @Inject public DefaultLoadBalancerResolver(
            BeanContext beanContext,
            List<ServiceInstanceList> serviceInstanceLists) {
        this.beanContext = beanContext;
        if (CollectionUtils.isNotEmpty(serviceInstanceLists)) {
            this.serviceInstanceLists = new HashMap<>(serviceInstanceLists.size());
            for (ServiceInstanceList provider : serviceInstanceLists) {
                this.serviceInstanceLists.put(provider.getID(), provider);
            }
        } else {
            this.serviceInstanceLists = Collections.emptyMap();
        }
    }

    @Override
    public Optional<? extends LoadBalancer> resolve(String... serviceReferences) {
        if (ArrayUtils.isEmpty(serviceReferences) || StringUtils.isEmpty(serviceReferences[0])) {
            return Optional.empty();
        }
        String reference = serviceReferences[0];

        if (reference.startsWith("/")) {
            // current server reference
            if (beanContext.containsBean(EmbeddedServer.class)) {
                EmbeddedServer embeddedServer = beanContext.getBean(EmbeddedServer.class);
                URL url = embeddedServer.getURL();
                return Optional.of(LoadBalancer.fixed(url));
            } else {
                return Optional.empty();
            }
        } else if (reference.indexOf('/') > -1) {
            try {
                URL url = new URL(reference);
                return Optional.of(LoadBalancer.fixed(url));
            } catch (MalformedURLException e) {
                return Optional.empty();
            }
        } else {
            reference = NameUtils.hyphenate(reference);
            return resolveLoadBalancerForServiceID(reference);
        }
    }

    /**
     * @param serviceID The service Id
     * @return An {@link Optional} with the load balancer
     */
    protected Optional<? extends LoadBalancer> resolveLoadBalancerForServiceID(String serviceID) {
        if (serviceInstanceLists.containsKey(serviceID)) {
            ServiceInstanceList serviceInstanceList = serviceInstanceLists.get(serviceID);
            LoadBalancer loadBalancer = beanContext.getBean(ServiceInstanceListLoadBalancerFactory.class).create(serviceInstanceList);
            return Optional.ofNullable(loadBalancer);
        } else {
            LoadBalancer loadBalancer = beanContext.getBean(DiscoveryClientLoadBalancerFactory.class).create(serviceID);
            return Optional.of(loadBalancer);
        }
    }
}
